# GoalZone end-to-end QA matrix

Run date: 2026-07-25

The automated suite exercises Spring Security, controllers, services, and JPA
against isolated H2 data. Browser smoke checks use the actual React application.

## Automated evidence

| Area | Result | Evidence |
| --- | --- | --- |
| Backend compilation/tests | Pass | 16 tests, 0 failures, 0 errors, 0 skipped. |
| Frontend production build | Pass | Vite production bundle builds successfully. |
| Customer account access | Pass | Own profile update succeeds; Admin update of a Customer profile returns 403. |
| UC-07 account lock | Pass | Lock requires a reason, blocks login, revokes tokens, and produces notification/email delivery evidence. |
| UC-09 activity authorization | Pass | Admin request returns 200; Staff request returns 403. |
| Field/pricing/service/slot | Pass | Admin/Staff APIs and browser workspaces cover create/update/status and daily operations. |
| Booking lifecycle | Pass | Online/walk-in creation, payment, reschedule, cancel, check-in, complete, no-show, and conflict guards are covered. |
| Payment/refund/invoice | Pass | Customer PayPal Sandbox and Staff cash flows, invoice reconciliation, and refunds are covered. |
| Promotion/membership/report | Pass | Admin management/report APIs and Customer membership/promotion flows are covered. |
| Supabase migration | Pass | Flyway validated 15 migrations and applied V15; schema is at version 15. |

## Final scope matrix

| UC range | Verdict | Notes |
| --- | --- | --- |
| UC-01 - UC-06 | Pass | Account registration, authentication, self profile, and recovery. |
| UC-07 | Pass | Admin View and Lock/Unlock only; no Customer edit action or API authority. |
| UC-08 | Pass | Staff account administration and invitation. |
| UC-09 | Pass | Admin-only Customer activity status. |
| UC-10 - UC-22 | Pass | Field, pricing, slot, service, and issue workflows; reviews excluded. |
| UC-23 - UC-36 | Pass | Booking lifecycle and conflict handling. |
| UC-37 - UC-49 | Pass | Checkout, payment, invoice, refund, and policy workflows. |
| UC-50 - UC-61 | Pass | Promotions, membership, notifications, and reports. |
| UC-62 - UC-63 | Pass | Rule-based assistant and suggested live slots. |

## Manual external checks

- SMTP mailbox receipt depends on the configured Gmail account.
- A fresh PayPal Sandbox buyer approval remains a short presentation smoke
  check because external buyer login is intentionally not automated.
