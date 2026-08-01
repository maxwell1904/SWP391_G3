from __future__ import annotations

from pathlib import Path
import re

from update_imported_rds_sds import FINAL_USE_CASES, normalized_sequence_filename


ROOT = Path(__file__).resolve().parents[1]
SEQUENCE = ROOT / "docs" / "Sequence"
CLASS = ROOT / "docs" / "Class Diagram"
ARCHIVE = ROOT / "docs" / "archive" / "retired-uc-diagrams-2026-08-01"


def sequence_source(final_number: int, old_number: int) -> Path:
    if final_number == 7:
        return SEQUENCE / "UC-07-manage-customer-accounts.puml"
    if final_number == 8:
        return SEQUENCE / "UC-07-supplement-lock-unlock-customer-account.puml"
    matches = sorted(SEQUENCE.glob(f"UC-{old_number:02d}-*.puml"))
    if len(matches) != 1:
        raise RuntimeError(f"Expected one old sequence source for UC-{old_number:02d}, found {matches}")
    return matches[0]


def rewrite_sequence(content: str, old_number: int, final_number: int, final_name: str) -> str:
    old_id = f"UC-{old_number:02d}"
    new_id = f"UC-{final_number:02d}"
    content = content.replace(old_id, new_id)
    title = f"== {new_id}: {final_name} =="
    if re.search(r"(?m)^== .* ==$", content):
        content = re.sub(r"(?m)^== .* ==$", title, content, count=1)
    else:
        lines = content.splitlines()
        arrow = next((index for index, line in enumerate(lines) if " -> " in line or " --> " in line), len(lines) - 1)
        lines[arrow:arrow] = [title, ""]
        content = "\n".join(lines) + ("\n" if content.endswith("\n") else "")
    return content


def migrate_sequences() -> None:
    entries = []
    for final_number, use_case in enumerate(FINAL_USE_CASES, start=1):
        old_number, _, _, _, final_name, _ = use_case
        if final_number in {16, 17}:
            continue
        source = sequence_source(final_number, old_number)
        target = SEQUENCE / normalized_sequence_filename(final_number, final_name).replace(".png", ".puml")
        entries.append((source, target, old_number, final_number, final_name, source.read_text()))

    archive = ARCHIVE / "Sequence"
    archive.mkdir(parents=True, exist_ok=True)
    for path in sorted(SEQUENCE.glob("UC-*.puml")):
        destination = archive / path.name
        if destination.exists():
            destination.unlink()
        path.replace(destination)

    for _, target, old_number, final_number, final_name, content in entries:
        target.write_text(rewrite_sequence(content, old_number, final_number, final_name))


def archive_retired_class_diagrams() -> None:
    retired = {
        "ask-smart-assistant-for-available-fields.puml",
        "capture-online-payment.puml",
        "check-extra-service-availability.puml",
        "confirm-booking.puml",
        "generate-booking-invoice.puml",
        "get-suggested-available-slots.puml",
        "handle-booking-conflict.puml",
        "manage-unavailable-slots.puml",
        "preview-cancellation-fee-refund.puml",
        "reject-booking.puml",
    }
    archive = ARCHIVE / "Class Diagram"
    archive.mkdir(parents=True, exist_ok=True)
    for name in sorted(retired):
        source = CLASS / name
        if not source.exists():
            continue
        destination = archive / name
        if destination.exists():
            destination.unlink()
        source.replace(destination)


if __name__ == "__main__":
    migrate_sequences()
    archive_retired_class_diagrams()
    print("Migrated 54 retained sequence diagrams; split UC-16/17 sources are added separately.")
