# Implemented use cases

Status date: 2026-07-22

| Range | Status | Code-first note |
| --- | --- | --- |
| UC-01–06 | Implemented | Registration/verification, login/logout, profile, self-owned password change and reset. |
| UC-07 | Implemented | Admin customer directory/activity and active/locked sign-in status with notification. |
| UC-08 | Implemented | Admin creates Staff identity/status; Staff receives invitation and owns the password. |
| UC-09–10 | Implemented | Customer activity plus independent new-booking restriction/restoration. |
| UC-11–23 | Implemented | Field catalogue/detail/search, image upload, pricing, slot operation, services and issue lifecycle. Reviews are excluded. |
| UC-24–37 | Implemented | Online/walk-in creation, detail/history/calendar and guarded lifecycle, reschedule, cancellation/refund preview, no-show and conflict resolution. |
| UC-38–50 | Implemented | Checkout, Customer PayPal Sandbox, Staff cash, payment history, invoice, refund request/processing and policy settings. |
| UC-51–62 | Implemented | Promotions, membership rules/progress, notifications and Admin revenue/booking/customer reports. |
| UC-63–64 | Implemented with scoped design | Rule-based availability assistant and ranked live slots. External LLM/RAG is optional. |

Detailed acceptance criteria, roles, route/API paths and evidence are maintained
in `END_TO_END_QA_MATRIX.md`. Scope decisions and non-overlapping UC definitions
are maintained in `BACKLOG_CODE_FIRST_DELIVERY.md`.
