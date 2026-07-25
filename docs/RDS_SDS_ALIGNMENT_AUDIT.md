# RDS/SDS alignment audit

Audit date: 2026-07-25

- RDS and SDS use the final UC-01 through UC-63 numbering.
- UC-07 provides read-only Customer View plus Lock/Unlock.
- UC-09 Customer activity is Admin-only.
- UC-04 profile update is self-only.
- `avatar_url` is absent from the final entity, DTO, UI, schema, and diagrams.
- Account lock fields are `account_locked` and `lock_reason`.
- Reviews are explicitly outside scope.
- PostgreSQL/Supabase migration status is Flyway V15.
- RDS includes ERD, database schema, package information, and UI designs.
- SDS includes the package diagram, database schema, class diagrams, sequence
  diagrams, class specifications, and database queries.
- Final Release references the final RDS/SDS filenames and V15 migration status.

Verification evidence:

- Backend tests: 16 passed.
- Frontend production build: passed.
- PlantUML source syntax: checked.
- Final DOCX structural and visual QA: completed for the updated pages and prior
  full-document render.
