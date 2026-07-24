# Backlog–RDS–code gap analysis

Audit date: 2026-07-22

This file records only remaining or deliberately excluded scope. The canonical
use-case decisions are in `BACKLOG_CODE_FIRST_DELIVERY.md`, and per-UC test
routes are in `END_TO_END_QA_MATRIX.md`.

## Current result

- UC-01 through UC-62 have implemented code paths and documented acceptance
  flows.
- UC-63/64 remain a deterministic, explainable availability assistant over live
  field, slot, price and promotion data. An external LLM/RAG integration is
  optional and is not claimed.
- UC-07 and UC-10 are retained as distinct use cases. UC-07 lock/unlock controls
  sign-in; UC-10 restrict/unrestrict controls only creation of new bookings.
- Customer submits refund requests. Staff/Admin review and process them. PayPal
  refunds are completed only after provider `COMPLETED`; cash completion is an
  explicit venue action.
- Staff/Admin do not have customer online-checkout controls. Staff handles
  walk-in/counter cash operations; Admin audits records and policy.

## Deliberate exclusions

- Field ratings/reviews: no entity, moderation workflow or backlog-approved
  acceptance criteria.
- Production PayPal certification/live-money settlement: classroom deployment
  uses Sandbox credentials.
- Multiple promotion-code stacking and enterprise inventory/accounting systems.
- External chatbot/RAG knowledge base; the current assistant does not fabricate
  data and is kept separate from ordinary Find a slot.

## External checks still requiring a human account

<<<<<<< HEAD
- Recreated `docs/IMPLEMENTED_USE_CASES.md` because the previous file mapped several UC IDs incorrectly.
- Updated `database/football_field_booking_slim.dbml` so `app_user` includes email verification fields used by the code-first entity.
- Added a PostgreSQL implementation index note for `app_user.email_verification_token`.
- Added profile editing UI/API support for UC-04.
- Added Admin customer lock/unlock UI support for UC-10.
- Added staff reject-booking support for UC-30 with pending-status guard and customer notification.
- Completed UC-39 with deposit/full selection in checkout and server-side payable amount validation.
- Completed UC-46 demo coverage with invoice/payment detail panels for customer and staff workspaces.
=======
- Confirm that the Sandbox buyer sees the captured/refunded transaction in the
  PayPal Sandbox dashboard.
- Confirm delivered SMTP messages in the actual mailbox (verification, reset,
  staff invitation, booking-access restriction/restoration, account lock and
  booking/refund notifications).
- Replace temporary classroom URLs/secrets before any public deployment.
>>>>>>> 327a19993fe956540087376c122302c65ddffcde

No earlier “partial demo” statements are authoritative after this audit.
