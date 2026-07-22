# GoalZone code-first backlog

Updated: 2026-07-22. This is the scope used to write RDS/SDS/test documents.
The original Google Sheet remains planning history; where it conflicts with
working code and the decisions below, this file is authoritative.

## Scope decisions

| Original item | Code-first decision |
| --- | --- |
| UC-07 / UC-10 | Keep both. UC-07 manages Customer profile and **active/locked sign-in status**. UC-10 independently restricts **new booking creation**. A booking-restricted Customer can still sign in, see history, pay, reschedule, cancel and request an eligible refund. A locked Customer cannot sign in. Both actions notify by email and in-app record. |
| UC-08 | Admin creates Staff identity/status only. The system emails a one-hour setup link so Staff chooses the password; Admin cannot set or later change it. |
| UC-12 reviews | Field detail is delivered; reviews are deferred because there is no review entity or moderation flow. Do not claim reviews in the SRS acceptance criteria. |
| UC-21 | Both the owning Customer and Staff can edit add-ons while a booking is pending/confirmed. Totals, discounts, balance/refund delta and invoice are recalculated together. |
| UC-39/40 | Online PayPal sandbox belongs only to Customer. Staff creates walk-in bookings and records cash deposit/full/remaining payment. Admin does neither checkout flow. |
| UC-47/48 | Only the booking owner submits a refund request. Staff/Admin review it. PayPal `completed` is accepted only from the provider; cash completion remains an explicit venue operation. After completion, gross paid stays auditable while refund, invoice, refundable balance, and payment partial/full-refund status are reconciled. |
| UC-60 | Revenue is gross collected minus completed refunds and exact PayPal processor fees. Fees are read from the provider response, never estimated or hard-coded. |
| UC-52/53 | Only Admin manages campaigns. One promotion code can be applied to a booking; multi-code stacking is out of scope. Eligibility supports field type, required service, membership, day, time, minimum amount, date and usage limit. |
| Membership period | Levels support lifetime, monthly, and consecutive-week completed-booking thresholds. |
| UC-63 | Delivered as an explainable, rule-based availability assistant; external AI/LLM integration remains optional. |
| UC-64 | Delivered as ranked available-slot suggestions using availability, time preference, budget, and eligible campaigns. |

## Delivery backlog

| Area | Done | Partial / deferred |
| --- | --- | --- |
| Account & access | UC-01–11 | None |
| Field, slot, service, support | UC-12–23 | UC-12 field detail Done, reviews Deferred |
| Booking lifecycle | UC-24–37 | None |
| Checkout, payment, invoice, policies | UC-38–50 | External PayPal buyer/refund confirmation remains a sandbox smoke check |
| Promotion, membership, notifications, reports | UC-51–62 | External email delivery is configuration-smoked, not asserted by automated tests |
| Discovery/AI | UC-63–64 | Rule-based assistant and slot suggestions; external AI/LLM is optional |

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
- PostgreSQL schema changes are Flyway-owned (currently V1–V11) and Hibernate
  runs with `ddl-auto=validate`. The local H2 fallback is file-backed so a
  mistaken local launch does not erase history; automated tests override it
  with an isolated in-memory database.

## Documents not to claim

- No production/live-money PayPal certification; classroom delivery targets PayPal Sandbox.
- No field review/rating module.
- No chatbot/RAG or multi-promotion stacking; the availability assistant is rule-based.
