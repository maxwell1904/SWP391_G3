# GoalZone code-first backlog decisions

Updated: 2026-08-01. This file records the implemented decisions shared by the 56-row backlog, RDS, SDS, code, tests, and Flyway V21 schema.

| Area | Canonical decision |
| --- | --- |
| Customer administration | UC-07 is read-only Customer inspection; UC-08 is the separate security-sensitive Lock/Unlock flow. Admin cannot edit Customer profile data. |
| Customer activity | UC-10 is one-Customer operational detail; UC-56 is an aggregate period report. Raw IDs are never presented as useful content. |
| Slot calendar | UC-16 configures automatic generation rules; UC-17 records Staff block exceptions; UC-18 displays the daily operation calendar. |
| Add-ons | UC-20 adds services during booking; UC-21 edits them before start/check-in. Availability is included validation, not a standalone UC. |
| Booking approval | There is no manual Confirm/Reject queue. PayPal/cash business rules establish valid state automatically. Historical statuses remain schema-compatible. |
| Walk-in identity | Staff may select a registered Customer by phone/email or record a first-time visitor's name and phone on the booking. Guest contact is not an account and receives no membership or PayPal flow. |
| Cancellation/conflict | Cancellation preview is included in UC-30. Conflict handling composes issue, reschedule, cancellation, refund, and notification flows. |
| Payments | UC-36 is the Customer PayPal Sandbox journey; provider capture is an idempotent internal step. Staff records only real on-site cash. |
| Invoice | Invoice generation is a recalculated system postcondition; UC-40 is the authorized invoice/payment-status view. |
| Refunds | The owner requests; Venue Staff reviews/processes; Admin is audit-only; eligible PayPal execution is idempotent and only provider `COMPLETED` is final. |
| Availability discovery | UC-13 deterministic date/time search is authoritative. Assistant/Gemini and ranked-suggestion UI/API were removed. |

## Final ranges

| Range | Owner area |
| --- | --- |
| UC-01–UC-10 | Account and access |
| UC-11–UC-23 | Fields, slots, services, and issues |
| UC-24–UC-33 | Booking and venue operations |
| UC-34–UC-44 | Checkout, payments, refunds, and policy |
| UC-45–UC-56 | Promotions, membership, notifications, and reports |

## Delivery rules

- PostgreSQL schema changes are Flyway-owned through V21; production uses `ddl-auto=validate`.
- H2 remains disposable for automated tests.
- USD is both displayed and sent to PayPal Sandbox; processor fees are merchant costs, not customer tax.
- Bookable slots must match an active price rule; there is no hard-coded fallback.
- Booking transitions, ownership, payment rules, slot uniqueness, promotion reservation, and add-on stock are enforced server-side.
- Customer access uses `app_user.status` plus `lock_reason`; `auth_version` revokes old JWTs.
- Image upload accepts JPEG/PNG/WebP/GIF up to 5 MB.
