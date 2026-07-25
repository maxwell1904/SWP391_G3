# Implemented use cases

Status date: 2026-07-25

The final backlog contains 63 use cases after removal of the obsolete
booking-restriction item and renumbering of every later use case.

| Range | Status | Delivered behavior |
| --- | --- | --- |
| UC-01 - UC-06 | Implemented | Registration/email verification, login/logout, self-owned profile update, password change, and password reset. |
| UC-07 | Implemented | Admin Customer directory with read-only View and Lock/Unlock only. Lock reason, login blocking, token revocation, email, and notification are covered. |
| UC-08 | Implemented | Admin creates and maintains Staff identity/status; Staff owns the invited password. |
| UC-09 | Implemented | Admin-only Customer activity detail. Staff is denied by API and has no UI panel. |
| UC-10 - UC-22 | Implemented | Field catalogue/detail/search, field and pricing management, slots/calendar, extra services, and issue reporting/resolution. Reviews are excluded. |
| UC-23 - UC-36 | Implemented | Online/walk-in booking, detail/history/calendar, guarded lifecycle, reschedule, cancellation preview, cancellation, check-in, completion, no-show, and conflict protection. |
| UC-37 - UC-49 | Implemented | Checkout, PayPal Sandbox, Staff cash, payment history, invoice, refund request/processing, and policy settings. |
| UC-50 - UC-61 | Implemented | Promotions, membership rules/progress, notifications, and Admin revenue/booking/Customer reports. |
| UC-62 - UC-63 | Implemented with scoped design | Rule-based availability assistant and ranked live slots. External LLM/RAG is optional. |

## Verification

- Backend: `mvn test` covers 16 integration/service tests.
- Frontend: `npm run build` completes successfully.
- PostgreSQL/Supabase: Flyway schema version V15.
- RDS, SDS, and Final Release use the final UC-01 - UC-63 numbering.
