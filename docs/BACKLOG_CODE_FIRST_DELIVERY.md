# GoalZone code-first backlog

Updated: 2026-07-26. This file records the final implemented scope used by the
RDS, SDS, Final Release, and automated tests.

## Scope decisions

| Area | Final decision |
| --- | --- |
| Customer administration | UC-07 provides a read-only View action plus Lock/Unlock. Admin cannot edit a Customer profile. Lock requires a reason, revokes active tokens, blocks login, and sends email/in-app notification evidence. |
| Customer activity | UC-09 is Admin-only. Staff has no Customer activity API or UI access. |
| Personal profile | Every authenticated user may update only their own name, phone, and address. `avatar_url` is not part of the final model. |
| Reviews | Field reviews/ratings are outside the approved scope and are not modelled. |
| Pricing and discounts | The USD subtotal is field plus services. One promotion is capped at the subtotal; `stackable=false` prevents an additional membership discount. Membership is calculated on the non-negative post-promotion balance. |
| Payments | Customer online checkout uses PayPal Sandbox. Staff venue operations record cash payments. PayPal receives the exact final booking/deposit balance; processor fee and merchant net are captured from the provider response and are not added as customer tax. |
| Refunds | The booking owner requests a refund; Staff/Admin process it. `paidAmount` remains gross collected value, completed refunds are recorded separately, and pending requests reserve refundable capacity once. Provider `COMPLETED` is authoritative for PayPal refunds. |
| Slot calendar | Admin configures opening time, closing time, slot duration, and a 1–90 day rolling horizon. The backend materializes availability automatically; Staff Block/Unblock records exceptional closures or reserved periods. There is no ordinary manual Add Slot workflow. |
| Availability assistant | UC-62 is Done and shippable: Gemini interprets and phrases the Customer's natural-language request, while GoalZone data remains authoritative. `GEMINI_API_KEY` and `GEMINI_MODEL` are runtime deployment configuration rather than unfinished backlog scope. UC-63 deterministically ranks live field, slot, price, membership, and promotion data and remains independently usable without Gemini. |

## Final numbering

The removed booking-restriction use case is not present. All later use cases
were shifted down by one, so the final backlog contains UC-01 through UC-63:

| Range | Area |
| --- | --- |
| UC-01 - UC-09 | Account and Customer Status |
| UC-10 - UC-22 | Field, Slot, Service, and Issue Management |
| UC-23 - UC-36 | Booking Lifecycle |
| UC-37 - UC-49 | Payment Gateway, Refund, and Invoice |
| UC-50 - UC-61 | Promotion, Membership, Notification, and Report |
| UC-62 - UC-63 | Availability assistant and suggested slots |

## Delivery rules

- PostgreSQL schema changes are Flyway-owned through V18; Hibernate uses
  `ddl-auto=validate` for PostgreSQL.
- H2 remains disposable for local automated tests.
- USD is the display and PayPal settlement currency.
- GoalZone does not calculate VAT/sales tax in the classroom scope. PayPal processor fees are merchant costs, not customer tax.
- PayPal capture and refund mutations use stable idempotency identities and reconcile provider state after an ambiguous timeout.
- Booking state transitions and slot uniqueness are server-enforced.
- Image upload accepts JPEG/PNG/WebP/GIF up to 5 MB.
- Customer access has one persisted source of truth: `app_user.status`.
  `accountLocked` remains only a derived API compatibility field.
- No production/live-money PayPal certification is claimed.
