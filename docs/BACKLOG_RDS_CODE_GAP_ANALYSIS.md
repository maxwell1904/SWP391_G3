# Backlog-RDS-code gap analysis

Audit date: 2026-07-25

## Result

- RDS, SDS, Final Release, frontend, backend, and Supabase use the same final
  UC-01 through UC-63 numbering.
- UC-07 exposes Admin View and Lock/Unlock only; Customer profile editing is
  self-owned.
- UC-09 Customer activity detail is Admin-only.
- `avatar_url` is removed from the entity, DTO, UI, diagrams, and Supabase by
  Flyway V15.
- Account access uses `account_locked` and `lock_reason`. Obsolete
  booking-restriction behavior is not part of the final backlog.
- Reviews/ratings and production PayPal certification remain deliberate
  exclusions.

## External presentation checks

- Confirm receipt of verification, password-reset, Staff invitation, and
  account-lock emails using the configured mailbox.
- Run one PayPal Sandbox buyer approval during the presentation environment
  smoke check.
- Keep `.env.local` and all credentials out of the submitted Git history.
