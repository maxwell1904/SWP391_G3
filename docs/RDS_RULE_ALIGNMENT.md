# RDS Rule Alignment

> Archived rule snapshot. Use `BACKLOG_CODE_FIRST_DELIVERY.md` and
> `END_TO_END_QA_MATRIX.md` for the current verified rules.

This file tracks the current code-first implementation against the RDS business rules. The canonical scope decisions are in `BACKLOG_CODE_FIRST_DELIVERY.md`; individual acceptance evidence is in `END_TO_END_QA_MATRIX.md`.

## Implemented

- BR-01/02/03: booking creation rejects active booking conflicts, past slots, and blocked slots.
- BR-04/05/06: online bookings start pending with a configured deposit amount; confirmation can happen after payment capture or staff approval.
- BR-07/08/09: check-in, completion, cancellation, rejection, and no-show transitions are guarded by the lifecycle service.
- BR-13/14/15: only completed bookings update membership progress; no-show/cancelled/expired bookings do not count.
- BR-16: checkout summary includes field price, services, promotion, membership, deposit, remaining amount, and total. Membership discount is applied only for online customer bookings, not guest preview or staff walk-in booking.
- BR-17/18: promotion validation covers active dates, usage limit, minimum amount, field type, service condition, and stores applied snapshots.
- BR-20/21/22/24: extra service quantity/stock checks, unique account email/phone, locked-customer blocking, and invoice summary are implemented.

## Completed workflow clarifications

- BR-10/11 and UC-32: the customer receives a cancellation/refund preview before confirming. The preview is explanatory; cancellation remains the only mutating action.
- BR-19 and UC-21: Customer or Staff can edit extra services before check-in. The booking total, discount snapshots, paid/refundable balance, service inventory, and invoice are recalculated atomically.
- BR-23: account deletion is outside the approved backlog. Lock/unlock is the security control; restriction/restoration independently controls only new booking creation.
- UC-16/23/31/37: unavailable-slot management, issue resolution/audit, rescheduling, and conflict handling are available through role-appropriate UI paths.
- UC-49/50: deposit, pending-payment timeout, early/same-day refund percentages, and reminder lead time are editable settings consumed by the live workflows.
- Payment sandbox: Customer checkout uses the PayPal JavaScript SDK plus server-side Orders v2 create/capture. Provider refunds use PayPal's refund API and are marked completed only after a provider `COMPLETED` response. Staff/Admin booking intake remains cash-only.
- Email: verification, password reset, Staff invitation, account lock/unlock, booking restriction/restoration, booking, and refund events use SMTP; the same events also create in-app notification records where applicable.
- Backlog/RDS/code decisions and remaining external smoke checks are tracked in `docs/BACKLOG_RDS_CODE_GAP_ANALYSIS.md`.

## Extension Points

- `AccountController`: account lock/status, independent booking restriction, email verification, profile, self-owned password change, and password setup/reset.
- `FieldOperationController`: field management, price management, slot blocking, issue resolution.
- `BookingController`: booking detail, lifecycle, reschedule, conflict handling, service editing, and cancellation/refund preview.
- `PaymentController`: payment records, PayPal sandbox create/capture/cancel, invoices, provider-fee tracking, and refund management.
- `PromotionReportController`: promotion, membership, settings, notifications, reports.
