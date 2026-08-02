#!/usr/bin/env python3
"""Generate the canonical editable GoalZone use-case diagrams.

The catalogue is read from update_imported_rds_sds.py with ast.literal_eval so
the diagram labels cannot silently drift from the RDS/SDS catalogue.
"""

from __future__ import annotations

import argparse
import ast
from dataclasses import dataclass
from html import escape
from pathlib import Path
import shutil
import subprocess
import textwrap
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
CATALOGUE_SOURCE = ROOT / "docs" / "update_imported_rds_sds.py"
OUTPUT_DIR = ROOT / "docs" / "Use Case Diagram"
MASTER_OUTPUT = OUTPUT_DIR / "GoalZone_Use_Case_Diagrams.drawio"
PREVIEW_DIR = OUTPUT_DIR / "previews"


@dataclass(frozen=True)
class Page:
    slug: str
    title: str
    actors: tuple[str, ...]
    use_cases: tuple[int, ...]
    associations: dict[str, tuple[int, ...]]
    relations: tuple[tuple[int, int, str], ...] = ()
    generalizations: tuple[tuple[str, str], ...] = ()
    positions: tuple[tuple[int, int, int], ...] = ()


PAGES = (
    Page(
        "01-guest",
        "Guest Use Case Diagram",
        ("Guest",),
        (1, 2, 6, 11, 12, 13, 45, 50),
        {
            "Guest": (1, 2, 6, 11, 45, 50),
        },
        ((12, 11, "extend"), (13, 11, "extend")),
        positions=((1, 0, 0), (2, 0, 1), (6, 0, 2), (11, 1, 0), (13, 1, 2), (45, 2, 0), (50, 2, 1), (12, 2, 2)),
    ),
    Page(
        "02-authenticated-account",
        "Authenticated Account Use Case Diagram",
        ("Authenticated User", "Customer"),
        (3, 4, 5, 48),
        {"Authenticated User": (3, 4, 5), "Customer": (48,)},
        generalizations=(("Customer", "Authenticated User"),),
    ),
    Page(
        "03-customer-create-booking",
        "Customer Create Booking Use Case Diagram",
        ("Customer",),
        (20, 24, 47, 34, 35, 36),
        {"Customer": (24,)},
        (
            (20, 24, "extend"),
            (24, 34, "include"),
            (34, 35, "include"),
            (35, 36, "include"),
            (47, 34, "extend"),
        ),
        positions=((24, 0, 0), (20, 0, 1), (34, 1, 0), (35, 1, 1), (36, 1, 2), (47, 2, 0)),
    ),
    Page(
        "04-customer-manage-booking",
        "Customer Manage Booking Use Case Diagram",
        ("Customer",),
        (27, 26, 21, 29, 30, 42, 22, 39, 40),
        {"Customer": (27, 22, 39, 40)},
        (
            (26, 27, "extend"),
            (21, 26, "extend"),
            (29, 26, "extend"),
            (30, 26, "extend"),
            (42, 26, "extend"),
        ),
    ),
    Page(
        "05-staff-counter-booking",
        "Staff Availability and Counter Booking Use Case Diagram",
        ("Venue Staff",),
        (17, 18, 20, 25, 34, 35),
        {"Venue Staff": (17, 18, 25)},
        (
            (20, 25, "extend"),
            (25, 34, "include"),
            (34, 35, "include"),
        ),
    ),
    Page(
        "06-staff-booking-operations",
        "Staff Booking Operations Use Case Diagram",
        ("Venue Staff",),
        (28, 26, 21, 29, 30, 31, 32, 33, 22),
        {"Venue Staff": (28, 22)},
        (
            (26, 28, "extend"),
            (21, 26, "extend"),
            (29, 26, "extend"),
            (30, 26, "extend"),
            (31, 26, "extend"),
            (32, 26, "extend"),
            (33, 26, "extend"),
        ),
    ),
    Page(
        "07-staff-support-and-billing",
        "Staff Support and Billing Use Case Diagram",
        ("Venue Staff",),
        (22, 23, 39, 40, 37, 42, 41),
        {"Venue Staff": (22, 23, 39, 40, 42)},
        ((37, 40, "extend"), (41, 42, "extend")),
    ),
    Page(
        "08-admin-account-and-venue-configuration",
        "Admin Account and Venue Configuration Use Case Diagram",
        ("Admin",),
        (7, 8, 10, 9, 14, 15, 16, 19),
        {"Admin": (7, 9, 14, 15, 16, 19)},
        ((8, 7, "extend"), (10, 7, "extend")),
    ),
    Page(
        "09-admin-commercial-configuration",
        "Admin Commercial Configuration Use Case Diagram",
        ("Admin",),
        (43, 44, 46, 49),
        {"Admin": (43, 44, 46, 49)},
    ),
    Page(
        "10-admin-audit-and-reports",
        "Admin Audit and Reports Use Case Diagram",
        ("Admin",),
        (39, 42, 54, 55, 56),
        {"Admin": (39, 42, 54, 55, 56)},
    ),
    Page(
        "11-automated-and-external",
        "Automated and External Use Case Diagram",
        ("Booking Event", "Scheduler", "PayPal Sandbox"),
        (51, 53, 47, 52, 38, 36, 41),
        {
            "Booking Event": (47, 51, 53),
            "Scheduler": (38, 52),
            "PayPal Sandbox": (36, 38, 41),
        },
    ),
)


def load_catalogue() -> dict[int, str]:
    tree = ast.parse(CATALOGUE_SOURCE.read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign):
            if any(isinstance(target, ast.Name) and target.id == "FINAL_USE_CASES" for target in node.targets):
                rows = ast.literal_eval(node.value)
                return {number: row[4] for number, row in enumerate(rows, start=1)}
    raise RuntimeError("FINAL_USE_CASES was not found")


def geometry(parent: ET.Element, *, x: float, y: float, width: float, height: float, relative: bool = False) -> None:
    attrs = {"as": "geometry", "x": str(x), "y": str(y), "width": str(width), "height": str(height)}
    if relative:
        attrs["relative"] = "1"
    ET.SubElement(parent, "mxGeometry", attrs)


def cell(
    root: ET.Element,
    cell_id: str,
    value: str,
    style: str,
    *,
    vertex: bool = False,
    edge: bool = False,
    source: str | None = None,
    target: str | None = None,
    x: float = 0,
    y: float = 0,
    width: float = 0,
    height: float = 0,
) -> ET.Element:
    attrs = {"id": cell_id, "value": value, "style": style, "parent": "1"}
    if vertex:
        attrs["vertex"] = "1"
    if edge:
        attrs["edge"] = "1"
    if source:
        attrs["source"] = source
    if target:
        attrs["target"] = target
    element = ET.SubElement(root, "mxCell", attrs)
    geometry(element, x=x, y=y, width=width, height=height, relative=edge)
    return element


def layout(page: Page) -> tuple[int, int, dict[int, tuple[float, float, float, float]], dict[str, tuple[float, float, float, float]]]:
    columns = 3
    position_overrides = {uc_id: (row, column) for uc_id, row, column in page.positions}
    rows = (len(page.use_cases) + columns - 1) // columns
    if position_overrides:
        rows = max(rows, max(row for row, _ in position_overrides.values()) + 1)
    canvas_width = 1800
    canvas_height = max(980, 250 + rows * 185)
    boundary_x, boundary_y = 340, 55
    ellipse_width, ellipse_height = 330, 112
    x_values = (470, 930, 1390)
    y_start, y_gap = 180, 185
    uc_boxes: dict[int, tuple[float, float, float, float]] = {}
    for index, uc_id in enumerate(page.use_cases):
        row, column = position_overrides.get(uc_id, divmod(index, columns))
        uc_boxes[uc_id] = (x_values[column], y_start + row * y_gap, ellipse_width, ellipse_height)

    actor_boxes: dict[str, tuple[float, float, float, float]] = {}
    preferred = []
    for actor in page.actors:
        related = [uc_boxes[uc_id][1] + ellipse_height / 2 for uc_id in page.associations[actor]]
        preferred.append((actor, sum(related) / len(related)))
    preferred.sort(key=lambda item: item[1])
    minimum_center = 180
    maximum_center = canvas_height - 150
    centers = []
    for _, proposed in preferred:
        center = max(minimum_center, proposed)
        if centers:
            center = max(center, centers[-1] + 205)
        centers.append(center)
    if centers and centers[-1] > maximum_center:
        shift = centers[-1] - maximum_center
        centers = [center - shift for center in centers]
    for (actor, _), center in zip(preferred, centers):
        actor_boxes[actor] = (70, center - 72, 170, 150)
    return canvas_width, canvas_height, uc_boxes, actor_boxes


def build_graph(page: Page, catalogue: dict[int, str]) -> ET.Element:
    canvas_width, canvas_height, uc_boxes, actor_boxes = layout(page)
    model = ET.Element(
        "mxGraphModel",
        {
            "dx": str(canvas_width),
            "dy": str(canvas_height),
            "grid": "1",
            "gridSize": "10",
            "guides": "1",
            "tooltips": "1",
            "connect": "1",
            "arrows": "1",
            "fold": "1",
            "page": "1",
            "pageScale": "1",
            "pageWidth": str(canvas_width),
            "pageHeight": str(canvas_height),
            "math": "0",
            "shadow": "0",
        },
    )
    root = ET.SubElement(model, "root")
    ET.SubElement(root, "mxCell", {"id": "0"})
    ET.SubElement(root, "mxCell", {"id": "1", "parent": "0"})

    cell(
        root,
        "boundary",
        "",
        "rounded=0;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#000000;strokeWidth=2;",
        vertex=True,
        x=340,
        y=55,
        width=1400,
        height=canvas_height - 110,
    )
    cell(
        root,
        "title",
        page.title,
        "text;html=1;align=center;verticalAlign=middle;resizable=0;points=[];autosize=0;fontSize=24;fontFamily=Arial;fontStyle=0;",
        vertex=True,
        x=650,
        y=75,
        width=780,
        height=50,
    )

    actor_style = (
        "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;"
        "outlineConnect=0;fillColor=#ffffff;strokeColor=#000000;strokeWidth=2;"
        "fontSize=18;fontFamily=Arial;"
    )
    for index, actor in enumerate(page.actors, start=1):
        x, y, width, height = actor_boxes[actor]
        cell(root, f"actor-{index}", actor, actor_style, vertex=True, x=x, y=y, width=width, height=height)

    ellipse_style = (
        "ellipse;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#000000;"
        "strokeWidth=2;fontSize=17;fontFamily=Arial;align=center;verticalAlign=middle;spacing=8;"
    )
    id_style = (
        "rounded=0;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#000000;"
        "strokeWidth=2;fontSize=16;fontFamily=Arial;align=center;verticalAlign=middle;"
    )
    for uc_id in page.use_cases:
        x, y, width, height = uc_boxes[uc_id]
        cell(root, f"uc-{uc_id}", catalogue[uc_id], ellipse_style, vertex=True, x=x, y=y, width=width, height=height)
        cell(root, f"label-{uc_id}", f"UC-{uc_id:02d}", id_style, vertex=True, x=x - 34, y=y - 26, width=108, height=46)

    association_style = "html=1;endArrow=classic;endFill=1;strokeColor=#000000;strokeWidth=2;rounded=0;"
    actor_ids = {actor: f"actor-{index}" for index, actor in enumerate(page.actors, start=1)}
    for actor in page.actors:
        for uc_id in page.associations[actor]:
            cell(
                root,
                f"assoc-{actor_ids[actor]}-{uc_id}",
                "",
                association_style,
                edge=True,
                source=actor_ids[actor],
                target=f"uc-{uc_id}",
            )

    relation_style = (
        "html=1;endArrow=classic;endFill=1;strokeColor=#000000;strokeWidth=2;"
        "dashed=1;dashPattern=6 6;rounded=0;fontSize=17;fontFamily=Arial;labelBackgroundColor=#ffffff;"
    )
    for index, (source, target, kind) in enumerate(page.relations, start=1):
        cell(
            root,
            f"relation-{index}",
            f"&lt;&lt;{kind}&gt;&gt;",
            relation_style,
            edge=True,
            source=f"uc-{source}",
            target=f"uc-{target}",
        )

    generalization_style = "html=1;endArrow=block;endFill=0;strokeColor=#000000;strokeWidth=2;rounded=0;"
    for index, (child, parent) in enumerate(page.generalizations, start=1):
        cell(
            root,
            f"generalization-{index}",
            "",
            generalization_style,
            edge=True,
            source=actor_ids[child],
            target=actor_ids[parent],
        )
    return model


def write_drawio(catalogue: dict[int, str]) -> None:
    mxfile = ET.Element(
        "mxfile",
        {
            "host": "app.diagrams.net",
            "modified": "2026-08-01T14:00:00.000Z",
            "agent": "Codex",
            "version": "24.7.17",
            "type": "device",
        },
    )
    for index, page in enumerate(PAGES, start=1):
        diagram = ET.SubElement(mxfile, "diagram", {"id": f"goalzone-uc-{index:02d}", "name": page.title})
        diagram.append(build_graph(page, catalogue))
    ET.indent(mxfile, space="  ")
    MASTER_OUTPUT.write_text(ET.tostring(mxfile, encoding="unicode", xml_declaration=True), encoding="utf-8")


def wrap_label(text: str, width: int = 27) -> list[str]:
    return textwrap.wrap(text, width=width, break_long_words=False, break_on_hyphens=False) or [text]


def svg_line_endpoint(source: tuple[float, float], box: tuple[float, float, float, float]) -> tuple[float, float]:
    cx, cy = box[0] + box[2] / 2, box[1] + box[3] / 2
    sx, sy = source
    dx, dy = sx - cx, sy - cy
    if dx == 0 and dy == 0:
        return cx, cy
    scale = 1 / max(abs(dx) / (box[2] / 2), abs(dy) / (box[3] / 2))
    return cx + dx * scale, cy + dy * scale


def write_svg(page: Page, catalogue: dict[int, str]) -> Path:
    width, height, uc_boxes, actor_boxes = layout(page)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        '<defs><marker id="arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto"><path d="M0,0 L12,6 L0,12 Z" fill="black"/></marker><marker id="open-triangle" markerWidth="14" markerHeight="14" refX="12" refY="7" orient="auto"><path d="M0,0 L14,7 L0,14 Z" fill="white" stroke="black" stroke-width="1.8"/></marker></defs>',
        f'<rect x="340" y="55" width="1400" height="{height - 110}" fill="white" stroke="black" stroke-width="3"/>',
        f'<text x="1040" y="112" text-anchor="middle" font-family="Arial" font-size="30">{escape(page.title)}</text>',
    ]

    actor_centers = {actor: (box[0] + box[2] / 2, box[1] + 58) for actor, box in actor_boxes.items()}
    for actor in page.actors:
        ax, ay = actor_centers[actor]
        for uc_id in page.associations[actor]:
            endpoint = svg_line_endpoint((ax + 40, ay), uc_boxes[uc_id])
            parts.append(
                f'<line x1="{ax + 34:.1f}" y1="{ay:.1f}" x2="{endpoint[0]:.1f}" y2="{endpoint[1]:.1f}" stroke="black" stroke-width="2.5" marker-end="url(#arrow)"/>'
            )

    for source, target, kind in page.relations:
        source_box, target_box = uc_boxes[source], uc_boxes[target]
        source_center = (source_box[0] + source_box[2] / 2, source_box[1] + source_box[3] / 2)
        target_center = (target_box[0] + target_box[2] / 2, target_box[1] + target_box[3] / 2)
        start = svg_line_endpoint(target_center, source_box)
        end = svg_line_endpoint(source_center, target_box)
        mx, my = (start[0] + end[0]) / 2, (start[1] + end[1]) / 2 - 10
        parts.extend(
            [
                f'<line x1="{start[0]:.1f}" y1="{start[1]:.1f}" x2="{end[0]:.1f}" y2="{end[1]:.1f}" stroke="black" stroke-width="2.5" stroke-dasharray="10 8" marker-end="url(#arrow)"/>',
                f'<rect x="{mx - 70:.1f}" y="{my - 22:.1f}" width="140" height="30" fill="white"/>',
                f'<text x="{mx:.1f}" y="{my:.1f}" text-anchor="middle" font-family="Arial" font-size="20">&lt;&lt;{kind}&gt;&gt;</text>',
            ]
        )

    for child, parent in page.generalizations:
        child_center, parent_center = actor_centers[child], actor_centers[parent]
        parts.append(
            f'<line x1="{child_center[0]:.1f}" y1="{child_center[1]:.1f}" x2="{parent_center[0]:.1f}" y2="{parent_center[1]:.1f}" stroke="black" stroke-width="2.5" marker-end="url(#open-triangle)"/>'
        )

    for actor in page.actors:
        x, y, box_width, _ = actor_boxes[actor]
        cx = x + box_width / 2
        parts.extend(
            [
                f'<circle cx="{cx:.1f}" cy="{y + 28:.1f}" r="20" fill="white" stroke="black" stroke-width="3"/>',
                f'<line x1="{cx:.1f}" y1="{y + 48:.1f}" x2="{cx:.1f}" y2="{y + 100:.1f}" stroke="black" stroke-width="3"/>',
                f'<line x1="{cx - 35:.1f}" y1="{y + 68:.1f}" x2="{cx + 35:.1f}" y2="{y + 68:.1f}" stroke="black" stroke-width="3"/>',
                f'<line x1="{cx:.1f}" y1="{y + 100:.1f}" x2="{cx - 34:.1f}" y2="{y + 142:.1f}" stroke="black" stroke-width="3"/>',
                f'<line x1="{cx:.1f}" y1="{y + 100:.1f}" x2="{cx + 34:.1f}" y2="{y + 142:.1f}" stroke="black" stroke-width="3"/>',
                f'<text x="{cx:.1f}" y="{y + 174:.1f}" text-anchor="middle" font-family="Arial" font-size="23">{escape(actor)}</text>',
            ]
        )

    for uc_id in page.use_cases:
        x, y, box_width, box_height = uc_boxes[uc_id]
        parts.append(
            f'<ellipse cx="{x + box_width / 2:.1f}" cy="{y + box_height / 2:.1f}" rx="{box_width / 2:.1f}" ry="{box_height / 2:.1f}" fill="white" stroke="black" stroke-width="3"/>'
        )
        lines = wrap_label(catalogue[uc_id])
        line_height = 24
        first_y = y + box_height / 2 - (len(lines) - 1) * line_height / 2 + 8
        for line_index, line in enumerate(lines):
            parts.append(
                f'<text x="{x + box_width / 2:.1f}" y="{first_y + line_index * line_height:.1f}" text-anchor="middle" font-family="Arial" font-size="21">{escape(line)}</text>'
            )
        parts.extend(
            [
                f'<rect x="{x - 34:.1f}" y="{y - 26:.1f}" width="108" height="46" fill="white" stroke="black" stroke-width="3"/>',
                f'<text x="{x + 20:.1f}" y="{y + 5:.1f}" text-anchor="middle" font-family="Arial" font-size="21">UC-{uc_id:02d}</text>',
            ]
        )
    parts.append("</svg>")
    output = PREVIEW_DIR / f"{page.slug}.svg"
    output.write_text("\n".join(parts) + "\n", encoding="utf-8")
    return output


def render_previews(catalogue: dict[int, str]) -> None:
    converter = shutil.which("rsvg-convert")
    expected = {f"{page.slug}{suffix}" for page in PAGES for suffix in (".svg", ".png")}
    for existing in PREVIEW_DIR.iterdir():
        if existing.is_file() and existing.suffix in {".svg", ".png"} and existing.name not in expected:
            existing.unlink()
    for page in PAGES:
        svg = write_svg(page, catalogue)
        if converter:
            subprocess.run(
                [converter, "-w", "2000", "-o", str(svg.with_suffix(".png")), str(svg)],
                check=True,
            )


def audit(catalogue: dict[int, str]) -> None:
    expected = set(catalogue)
    covered = {uc_id for page in PAGES for uc_id in page.use_cases}
    if expected != covered:
        raise SystemExit(f"Use-case coverage mismatch: missing={sorted(expected - covered)} extra={sorted(covered - expected)}")
    for page in PAGES:
        page_ids = set(page.use_cases)
        associated = {uc_id for values in page.associations.values() for uc_id in values}
        related = {uc_id for source, target, _ in page.relations for uc_id in (source, target)}
        documented = associated | related
        if page_ids != documented:
            raise SystemExit(
                f"{page.slug}: connectivity mismatch: unconnected={sorted(page_ids - documented)} unknown={sorted(documented - page_ids)}"
            )
        for source, target, kind in page.relations:
            if source not in page_ids or target not in page_ids or kind not in {"include", "extend"}:
                raise SystemExit(f"{page.slug}: invalid relation {(source, target, kind)}")
        if set(uc_id for uc_id, _, _ in page.positions) != (page_ids if page.positions else set()):
            raise SystemExit(f"{page.slug}: custom positions must cover every use case on that page")
    if MASTER_OUTPUT.exists():
        parsed = ET.parse(MASTER_OUTPUT).getroot()
        diagrams = parsed.findall("diagram")
        if len(diagrams) != len(PAGES):
            raise SystemExit(f"Expected {len(PAGES)} draw.io pages, found {len(diagrams)}")
        labels = {element.attrib["value"] for element in parsed.iter("mxCell") if element.attrib.get("id", "").startswith("label-")}
        expected_labels = {f"UC-{uc_id:02d}" for uc_id in expected}
        if labels != expected_labels:
            raise SystemExit("The draw.io UC-ID labels do not match UC-01 through UC-56")
    print(f"PASS: {len(PAGES)} draw.io pages cover {len(covered)} unique use cases (UC-01–UC-56).")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Validate the existing output without regenerating it")
    args = parser.parse_args()
    catalogue = load_catalogue()
    if not args.check:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
        write_drawio(catalogue)
        render_previews(catalogue)
    audit(catalogue)


if __name__ == "__main__":
    main()
