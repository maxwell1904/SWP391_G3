# Implemented use cases

Status date: 2026-08-01

The retake baseline contains 56 actor-goal use cases. This list is canonical together with the aligned backlog, RDS, SDS, code, and Flyway V21 schema.

| Range | Owner | Delivered behavior |
| --- | --- | --- |
| UC-01–UC-10 | BonVT | Registration/verification, login/logout, profile/password/recovery, Customer directory, lock/unlock security flow, Staff accounts, and single-Customer activity drill-down |
| UC-11–UC-23 | BaoNG | Field catalogue/search/management/pricing, Admin slot rules, Staff block exceptions, operation calendar, extra services, add-on create/edit validation, and issue reporting/resolution |
| UC-24–UC-33 | NgocPA | Online/walk-in booking, detail/history/calendar, reschedule, cancellation with included preview, check-in, completion, and no-show |
| UC-34–UC-44 | AnNP | Checkout, PayPal Sandbox, counter balance, failed/expired payment handling, payment/invoice views, refund review/provider execution, and booking policies |
| UC-45–UC-56 | AnPTT | Promotions, membership rules/progress/benefits, confirmation/reminder/cancellation/refund notifications, and three Admin reports |

## Scope decisions

- Old UC-07 was split into UC-07 View customer accounts and UC-08 Lock/unlock customer account.
- Old UC-15 was split into UC-16 Configure slot generation rules and UC-17 Block/unblock field slots.
- Extra-service availability, manual booking confirmation/rejection, cancellation preview, conflict handling, PayPal capture, and invoice generation remain included/internal behavior rather than standalone use cases.
- The availability assistant and ranked-suggestion page/API were removed. UC-13 ordinary availability search is authoritative.
- Historical `rejected` status and immutable Flyway migrations remain compatible, but the current UI/API no longer exposes a manual booking approval queue.

## Verification baseline

- Backend integration/service tests cover security, lifecycle, payment, promotion, refund, reporting, and scheduler rules.
- Frontend production build completes successfully.
- PostgreSQL/Supabase schema is managed through Flyway V21.
- RDS and SDS contain the final UC-01–UC-56 catalogue and code-design sections.
- The 56 class and 56 sequence PlantUML sources pass the repository audit.
