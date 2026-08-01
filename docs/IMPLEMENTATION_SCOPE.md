# Implementation scope

GoalZone is a classroom football-field booking system with 56 canonical use cases. The source-of-truth scope is defined by `LEAN_SCOPE_PROPOSAL_2026-08-01.md`, the aligned backlog, and `IMPLEMENTED_USE_CASES.md`.

## Included

- Account registration/verification, access, recovery, profile, Customer lock/unlock, Staff administration, and Customer activity.
- Field catalogue, deterministic priced availability, field/pricing/slot rules, Staff block exceptions, add-ons, and issues.
- Online and walk-in booking, reschedule, cancellation, check-in, completion, and no-show.
- PayPal Sandbox customer payment/refund, Staff cash payment, invoice/payment visibility, and configurable deposit/refund rules.
- Promotions, membership, notifications/reminders, and Admin revenue/booking/customer reports.

## Intentional exclusions and boundaries

- No availability assistant, LLM integration, ranked-suggestion page, or duplicate suggestion API. UC-13 is the single availability-search journey.
- No manual booking approval queue. Required payment automatically confirms an online booking; invalid/unavailable requests fail during create/pay.
- No reviews/ratings.
- No ordinary manual Add Slot action. Admin configures automatic generation rules; Staff records exceptional blocks.
- No separate inventory ledger. Add-on activity, limits, and overlapping stock are validated transactionally.
- PayPal is Sandbox only; no live-money certification is claimed.

## Run

Backend: `scripts/run-backend.sh`

Frontend: `scripts/run-frontend.sh`

Open `http://localhost:5173`.
