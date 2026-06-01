# RDS Rule Alignment

This file tracks the demo implementation against the RDS business rules so the remaining use cases can be added without changing the core model.

## Implemented

- BR-01/02/03: booking creation rejects active booking conflicts, past slots, and blocked slots.
- BR-04/05/06: online bookings start pending with a configured deposit amount; confirmation can happen after payment capture or staff approval.
- BR-07/08/09: check-in, completion, and cancellation transitions are guarded.
- BR-13/14/15: only completed bookings update membership progress; no-show/cancelled/expired bookings do not count.
- BR-16: checkout summary includes field price, services, promotion, membership, deposit, remaining amount, and total. Membership discount is applied only for online customer bookings, not guest preview or staff walk-in booking.
- BR-17/18: promotion validation covers active dates, usage limit, minimum amount, field type, service condition, and stores applied snapshots.
- BR-20/21/22/24: extra service quantity/stock checks, unique account email/phone, restricted customer blocking, and invoice summary are implemented.

## Partial / Next

- BR-10/11: cancellation/refund amount is calculated when cancelling; a separate preview endpoint/UI should be added for UC-32 before confirming cancellation.
- BR-19: services are selected at booking creation; update-before-check-in should be added as a dedicated UC-21 endpoint.
- BR-23: account deletion is not implemented yet, so deletion protection is not needed until that UC exists.
- Payment sandbox: current code stores deterministic demo payment records only. Real PayPal sandbox should wait until API credentials and redirect/callback flow are ready.
- Email verification: implemented as a local demo verification-link flow. Real email delivery needs SMTP/Supabase Auth or another provider later.

## Extension Points

- `AccountController`: account status, email verification, profile, and future deletion safeguards.
- `FieldOperationController`: field management, price management, slot blocking, issue resolution.
- `BookingController`: booking detail, lifecycle, future reschedule and cancellation preview.
- `PaymentController`: payment records, invoices, refund management, future PayPal callback.
- `PromotionReportController`: promotion, membership, settings, notifications, reports.
