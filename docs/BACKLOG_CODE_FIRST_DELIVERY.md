# GoalZone code-first backlog

Updated: 2026-07-21. This is the scope used to write SRS/SDD/test documents.
The original Google Sheet remains planning history; where it conflicts with
working code and the decisions below, this file is authoritative.

## Scope decisions

| Original item | Code-first decision |
| --- | --- |
| UC-07 / UC-10 | Keep both. UC-07 manages customer profile/account status; UC-10 independently restricts **new booking creation**. A restricted customer can still sign in, see history, cancel and request a refund. |
| UC-12 reviews | Field detail is delivered; reviews are deferred because there is no review entity or moderation flow. Do not claim reviews in the SRS acceptance criteria. |
| UC-21 | Both the owning Customer and Staff can edit add-ons while a booking is pending/confirmed. Totals, discounts, balance/refund delta and invoice are recalculated together. |
| UC-39/40 | Online PayPal sandbox belongs only to Customer. Staff creates walk-in bookings and records cash deposit/full/remaining payment. Admin does neither checkout flow. |
| UC-47 | Staff approval is followed by a captured-payment refund through PayPal Payments v2. `completed` is accepted only from PayPal; cash refunds remain an explicit venue operation. |
| UC-52/53 | Only Admin manages campaigns. One promotion code can be applied to a booking; multi-code stacking is out of scope. Eligibility supports field type, required service, membership, day, time, minimum amount, date and usage limit. |
| Membership period | Levels use lifetime completed-booking thresholds. Remove monthly/weekly examples unless period columns and reset jobs are added. |
| UC-63 | Deferred. A real chat assistant/RAG can be added after the assessed core flow; no fake assistant is shown. |
| UC-64 | Removed. Date/type slot search is UC-13; a separate “suggest slot” use case adds little value to this project. |

## Delivery backlog

| Area | Done | Partial / deferred |
| --- | --- | --- |
| Account & access | UC-01–11 | None |
| Field, slot, service, support | UC-12–23 | UC-12 field detail Done, reviews Deferred |
| Booking lifecycle | UC-24–37 | None |
| Checkout, payment, invoice, policies | UC-38–50 | External PayPal buyer/refund confirmation remains a sandbox smoke check |
| Promotion, membership, notifications, reports | UC-51–62 | External email delivery is configuration-smoked, not asserted by automated tests |
| Discovery/AI | — | UC-63 Deferred; UC-64 Removed |

Detailed acceptance evidence for every UC is maintained in
`docs/END_TO_END_QA_MATRIX.md`.

## Code-first domain rules

- USD is the only display and PayPal settlement currency.
- Booking state transitions are explicit: pending → confirmed/rejected/cancelled/expired;
  confirmed → checked-in/cancelled/no-show; checked-in → completed.
- Rescheduling preserves payment history and produces either a remaining
  balance or refundable price delta when the new slot price changes.
- Service inventory is checked across active bookings whose slots overlap in
  date/time, not only against the per-booking maximum.
- Image upload accepts JPEG/PNG/WebP/GIF up to 5 MB. Local filesystem storage
  is suitable for classroom deployment; use Supabase Storage/S3 for a
  multi-instance production deployment.
- PostgreSQL schema changes are Flyway-owned (currently V1–V9) and Hibernate
  runs with `ddl-auto=validate`. H2 remains disposable for local/test runs.

## Documents not to claim

- No production/live-money PayPal certification.
- No production/live-money PayPal certification; classroom delivery targets PayPal Sandbox.
- No field review/rating module.
- No chatbot/RAG or suggested-slot engine.
- No periodic membership reset or promotion stacking.
