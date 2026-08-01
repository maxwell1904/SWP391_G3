# GoalZone documentation map

This file identifies authoritative retake artifacts. Do not infer “latest” from a filename alone.

## Immutable round-1 evidence

`docs/submitted/round-1/` contains the exact lecturer-reviewed baseline and must not be overwritten.

## Current retake deliverables

- Backlog: `outputs/retake-refactor/GoalZone_Backlog_Retake_Aligned_2026-08-01.xlsx`
- RDS: `output/doc/GoalZone_RDS_Retake_Aligned_2026-08-01.docx` and `.pdf`
- SDS: `output/doc/GoalZone_SDS_Retake_Aligned_2026-08-01.docx` and `.pdf`
- Scope decision: `docs/LEAN_SCOPE_PROPOSAL_2026-08-01.md`
- Core rules: `docs/CORE_BUSINESS_RULES.md`
- Traceability: `docs/IMPLEMENTED_USE_CASES.md` and `docs/DIAGRAM_INDEX.md`

## Authoritative editable sources

- 56 class diagrams: `docs/Class Diagram/*.puml`
- 56 sequence diagrams: `docs/Sequence/*.puml`
- ERD: `docs/ERD/football_field_booking_erd_code_first.drawio.xml` and `docs/ERD/goalzone-code-first-schema.puml`
- Package diagram: `docs/Package Diagram/code-packages.puml`
- Document generator/audits: `docs/update_imported_rds_sds.py`, `docs/update_rds_screen_images.py`, `docs/replace_database_schema_diagram.py`, `docs/audit_puml_diagrams.py`, and `docs/audit_scope_alignment.py`

Use Case Diagram and Screen Flow are deliberately deferred until the team supplies the required draw.io and eraser.io templates. Earlier provisional sources are under `docs/archive/` and are not submitted/canonical.

## Archive policy

- `docs/archive/retired-uc-diagrams-2026-08-01/` preserves diagrams demoted or removed from the 56-UC set.
- `docs/archive/provisional-*` preserves temporary UC/screen-flow drafts.
- `docs/deliverables/` contains older generated comparisons, not retake deliverables.
- Temporary renders, caches, and Word lock files are disposable; immutable round-1 evidence is not.
- Final submission files receive a dated SHA-256 manifest.
