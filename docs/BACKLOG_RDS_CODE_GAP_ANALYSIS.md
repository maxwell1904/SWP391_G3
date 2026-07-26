# Backlog-RDS-code gap analysis

Audit date: 2026-07-26

## Result

- RDS, SDS, Final Release, frontend, backend, and Supabase use the same final
  UC-01 through UC-63 numbering.
- UC-07 exposes Admin View and Lock/Unlock only; Customer profile editing is
  self-owned.
- UC-09 Customer activity detail is Admin-only.
- `avatar_url` is removed from the entity, DTO, UI, diagrams, and Supabase;
  the current schema is Flyway V19.
- Account access uses `status = locked` as its single source of truth plus
  `lock_reason`. Obsolete
  booking-restriction behavior is not part of the final backlog.
- Reviews/ratings and production PayPal certification remain deliberate
  exclusions.
- The 19 JPA table mappings, DBML tables, and draw.io ERD entities match
  one-for-one. `field_type`, `field_price`, and `system_setting` are retained
  for normalized type metadata, effective pricing, and editable policy values.
- The backlog now treats automatic generation as part of UC-12/UC-15/UC-16:
  Admin controls the rule set; Staff Block/Unblock records exceptions; search
  and calendar reads materialize missing dates on demand.

## External presentation checks

- Confirm receipt of verification, password-reset, Staff invitation, and
  account-lock emails using the configured mailbox.
- Run one PayPal Sandbox buyer approval during the presentation environment
  smoke check.
- Keep `.env.local` and all credentials out of the submitted Git history.
- Configure `GEMINI_API_KEY` and run one UC-62 question before changing its
  backlog status from Deferred. UC-63 is already Done and does not depend on
  the external model.
