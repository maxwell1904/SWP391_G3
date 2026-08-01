#!/usr/bin/env python3
"""Replace stale Word TOC fields with compact, verified static contents.

The imported RDS/SDS templates contain cached Word fields that LibreOffice does
not refresh in headless mode.  This script derives the real page of each body
heading from the rendered PDF, then replaces the cached field with readable
static entries.  Run it once, render the DOCX again, and run it a second time
to account for any pagination change caused by the replacement.
"""

from __future__ import annotations

import argparse
import copy
import os
import tempfile
import zipfile
from pathlib import Path

from docx import Document
from lxml import etree
from pypdf import PdfReader


W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NS = {"w": W_NS}
W = f"{{{W_NS}}}"


def qn(local: str) -> str:
    return f"{W}{local}"


def compact(text: str) -> str:
    return " ".join(text.replace("\u00a0", " ").split())


def heading_entries(docx_path: Path, pdf_path: Path, mode: str):
    document = Document(docx_path)
    pages = [compact(page.extract_text() or "") for page in PdfReader(pdf_path).pages]
    allowed = {"Heading 1", "Heading 2", "Heading 3"} if mode == "rds" else {"Heading 1", "Heading 2"}
    levels = {"Heading 1": 1, "Heading 2": 2, "Heading 3": 3}
    entries: list[tuple[int, str, int]] = []

    for paragraph in document.paragraphs:
        style = paragraph.style.name if paragraph.style else ""
        title = compact(paragraph.text)
        if not title or style not in allowed:
            continue
        if title == "Record of Changes":
            entries.append((1, title, 2))
            continue
        matches = [i + 1 for i, text in enumerate(pages) if i + 1 > 3 and title in text]
        if not matches:
            raise RuntimeError(f"Cannot find heading in rendered PDF: {title!r}")
        entries.append((levels[style], title, matches[0]))

    return entries


def make_run(text: str, *, bold: bool = False, size_half_points: int = 19):
    run = etree.Element(qn("r"))
    rpr = etree.SubElement(run, qn("rPr"))
    etree.SubElement(rpr, qn("rFonts"), {qn("ascii"): "Arial", qn("hAnsi"): "Arial"})
    etree.SubElement(rpr, qn("sz"), {qn("val"): str(size_half_points)})
    etree.SubElement(rpr, qn("szCs"), {qn("val"): str(size_half_points)})
    if bold:
        etree.SubElement(rpr, qn("b"))
    node = etree.SubElement(run, qn("t"))
    node.text = text
    return run


def make_entry_paragraph(level: int, title: str, page: int, *, cell_width: int | None = None):
    paragraph = etree.Element(qn("p"))
    ppr = etree.SubElement(paragraph, qn("pPr"))
    tab_pos = 9200 if cell_width is None else max(2200, cell_width - 280)
    tabs = etree.SubElement(ppr, qn("tabs"))
    etree.SubElement(
        tabs,
        qn("tab"),
        {qn("val"): "right", qn("leader"): "dot", qn("pos"): str(tab_pos)},
    )
    etree.SubElement(ppr, qn("spacing"), {qn("before"): "0", qn("after"): "0", qn("line"): "210", qn("lineRule"): "exact"})
    if level > 1:
        etree.SubElement(ppr, qn("ind"), {qn("left"): str((level - 1) * (150 if cell_width else 240))})
    paragraph.append(make_run(title, bold=level == 1, size_half_points=17 if cell_width else 19))
    paragraph.append(etree.Element(qn("r")))
    etree.SubElement(paragraph[-1], qn("tab"))
    paragraph.append(make_run(str(page), bold=level == 1, size_half_points=17 if cell_width else 19))
    return paragraph


def make_two_column_table(entries: list[tuple[int, str, int]]):
    table = etree.Element(qn("tbl"))
    tbl_pr = etree.SubElement(table, qn("tblPr"))
    etree.SubElement(tbl_pr, qn("tblW"), {qn("w"): "0", qn("type"): "auto"})
    etree.SubElement(tbl_pr, qn("tblLayout"), {qn("type"): "fixed"})
    borders = etree.SubElement(tbl_pr, qn("tblBorders"))
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        etree.SubElement(borders, qn(edge), {qn("val"): "nil"})
    grid = etree.SubElement(table, qn("tblGrid"))
    for _ in range(2):
        etree.SubElement(grid, qn("gridCol"), {qn("w"): "4680"})

    midpoint = (len(entries) + 1) // 2
    columns = (entries[:midpoint], entries[midpoint:])
    for row_index in range(midpoint):
        row = etree.SubElement(table, qn("tr"))
        for column in columns:
            cell = etree.SubElement(row, qn("tc"))
            tc_pr = etree.SubElement(cell, qn("tcPr"))
            etree.SubElement(tc_pr, qn("tcW"), {qn("w"): "4680", qn("type"): "dxa"})
            etree.SubElement(tc_pr, qn("tcMar"))
            if row_index < len(column):
                level, title, page = column[row_index]
                cell.append(make_entry_paragraph(level, title, page, cell_width=4680))
            else:
                cell.append(etree.Element(qn("p")))
    return table


def replace_toc(docx_path: Path, pdf_path: Path, mode: str) -> None:
    entries = heading_entries(docx_path, pdf_path, mode)
    with zipfile.ZipFile(docx_path, "r") as source:
        document_xml = source.read("word/document.xml")
        root = etree.fromstring(document_xml)
        contents = root.xpath(".//w:sdt/w:sdtContent", namespaces=NS)
        if not contents:
            raise RuntimeError(f"No TOC content control found in {docx_path}")
        content = contents[0]
        for child in list(content):
            content.remove(child)

        if mode == "sds":
            content.append(make_two_column_table(entries))
        else:
            for level, title, page in entries:
                content.append(make_entry_paragraph(level, title, page))

        fd, temporary_name = tempfile.mkstemp(suffix=".docx", dir=docx_path.parent)
        os.close(fd)
        temporary = Path(temporary_name)
        try:
            with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as target:
                for item in source.infolist():
                    data = etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone="yes") if item.filename == "word/document.xml" else source.read(item.filename)
                    target.writestr(copy.copy(item), data)
            temporary.replace(docx_path)
        finally:
            temporary.unlink(missing_ok=True)

    print(f"Updated {mode.upper()} TOC with {len(entries)} verified entries: {docx_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("rds", "sds"))
    parser.add_argument("docx", type=Path)
    parser.add_argument("pdf", type=Path)
    args = parser.parse_args()
    replace_toc(args.docx, args.pdf, args.mode)


if __name__ == "__main__":
    main()
