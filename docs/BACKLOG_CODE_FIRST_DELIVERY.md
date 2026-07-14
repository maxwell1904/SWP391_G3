# Code-first backlog delivery

This file is the implementation source of truth for the current GoalZone
demo. It supersedes percentage values in the original backlog: those values
describe an earlier planning snapshot, not the code now in this repository.

## Backlog decisions to update

| Backlog item | Code-first decision |
| --- | --- |
| UC-07 Manage customer accounts and UC-10 lock/unlock booking ability | Keep UC-07 as the parent use case. Make UC-10 an alternate/sub-flow and acceptance criterion of UC-07. They share actor, data and UI; the restriction action is not a separate business capability. |
| UC-03 logout | A stateless JWT cannot literally be deleted server-side. The implementation increments `app_user.auth_version`; tokens issued before that version are rejected. |
| UC-47 online refund | The workflow is request → approve/reject → processing/completed and preserves provider fields. The final gateway call remains a PayPal production integration task; sandbox/demo completion is explicitly labelled as such. |
| UC-58 reminder | The in-process Spring scheduler sends one in-app reminder per booking. A multi-instance production deployment needs a durable job queue/lock. |
| UC-63 smart assistant / UC-64 suggested slots | Implemented as a rule-based live-availability recommender. Rename UC-63 to **Find field with guided criteria** unless the team later commits to a real LLM/RAG provider. An external AI API is not a prerequisite for the use case. |
| Membership rules | The original example describes monthly and weekly behaviour, while the implemented and documented model is lifetime completed-booking thresholds. Keep the current simple rule or add explicit period columns before claiming the monthly rule. |

## Delivered coverage

| Area | Delivered use cases |
| --- | --- |
| Account and access | UC-01–10: registration, verification, JWT login/logout revocation, profile/password/reset, Admin customer/staff management and activity, booking restriction. |
| Field operations | UC-11–23: field/pricing/service management, live availability, unavailable slots, operation calendar, pre-check-in service edits, issue report and resolution. |
| Booking lifecycle | UC-24–37: online/walk-in booking, detail/history/calendar, confirmation/rejection, reschedule, cancellation preview/cancel, check-in/completion/no-show and conflict prevention. |
| Payments | UC-38–50: checkout, deposit/full/remaining payment, PayPal sandbox create/capture, timeout expiry, history/invoice, refund request lifecycle and configurable policies. |
| Promotion, membership, notifications, reporting | UC-51–62: campaign and membership CRUD, benefit/progress, confirmation/cancellation/refund/reminder notifications, revenue/booking/customer reports. |
| Discovery | UC-63–64: guided availability endpoint and UI ranking available slots by time and budget. |

## Deliberate demo boundaries

- PayPal uses sandbox/mock support. Do not describe it as a live-money payment or live gateway refund.
- Reviews are not modelled; UC-12 displays this honestly rather than fabricating review data.
- Reports are aggregate operational reports, not a BI warehouse.
- The app uses Spring Security/JWT and PostgreSQL through the backend. It does not use Supabase Auth or expose Supabase keys to the browser.

## Database deployment rule

Use the `postgres` Spring profile only after running
`database/supabase/preflight.sql` and taking a backup. Flyway migrations are
the production schema source; the local `H2` profile remains code-first for
fast classroom development and tests. See `docs/SUPABASE_CONNECTION.md`.
