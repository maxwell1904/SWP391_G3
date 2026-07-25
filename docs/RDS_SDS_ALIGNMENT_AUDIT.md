# RDS/SDS alignment audit

Audit date: 2026-07-26

- RDS and SDS use the final UC-01 through UC-63 numbering.
- UC-07 provides read-only Customer View plus Lock/Unlock.
- UC-09 Customer activity is Admin-only.
- UC-04 profile update is self-only.
- `avatar_url` is absent from the final entity, DTO, UI, schema, and diagrams.
- Account lock uses `app_user.status = locked` as the single persisted source
  of truth plus `lock_reason`; the API derives `accountLocked` for UI compatibility.
- The JPA entity mappings, code-first DBML, and draw.io ERD contain the same 19
  physical tables.
- `field_type`, `field_price`, and `system_setting` remain intentional:
  reusable type metadata, effective time-band pricing, and Admin-editable
  policy values must not be duplicated on each `field` row.
- UC-12, UC-15, and UC-16 now share one slot model: Admin rules define the
  rolling calendar, the backend materializes slots, and Staff Block/Unblock
  persists exceptions without a manual Add Slot dependency.
- Reviews are explicitly outside scope.
- PostgreSQL/Supabase migration status is Flyway V19.
- RDS includes ERD, database schema, package information, and UI designs.
- SDS includes the package diagram, database schema, class diagrams, sequence
  diagrams, class specifications, and database queries.
- Final Release references the final RDS/SDS filenames and V18 migration status.

Verification evidence:

- Backend tests: 17 passed.
- Frontend production build: passed.
- PlantUML source syntax: checked.
- Live Supabase: PostgreSQL 17.6, Flyway V19; automatic-slot, account-lock, gross-payment, processor-fee, and refund-ledger verification SQL passed.
- UC-62 external smoke test: passed with configured Gemini integration and live GoalZone slot grounding
  returns HTTP 503. UC-63 deterministic ranking is automated and passed.
- Final DOCX structural and visual QA: completed for the updated pages and prior
  full-document render.
