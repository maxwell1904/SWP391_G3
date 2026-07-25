# GoalZone code-first backlog

Updated: 2026-07-25. This file records the final implemented scope used by the
RDS, SDS, Final Release, and automated tests.

## Scope decisions

| Area | Final decision |
| --- | --- |
| Customer administration | UC-07 provides a read-only View action plus Lock/Unlock. Admin cannot edit a Customer profile. Lock requires a reason, revokes active tokens, blocks login, and sends email/in-app notification evidence. |
| Customer activity | UC-09 is Admin-only. Staff has no Customer activity API or UI access. |
| Personal profile | Every authenticated user may update only their own name, phone, and address. `avatar_url` is not part of the final model. |
| Reviews | Field reviews/ratings are outside the approved scope and are not modelled. |
| Payments | Customer online checkout uses PayPal Sandbox. Staff venue operations record cash payments. |
| Refunds | The booking owner requests a refund; Staff/Admin process it. Provider `COMPLETED` is authoritative for PayPal refunds. |
| Availability assistant | UC-62 and UC-63 are deterministic, explainable suggestions over live field, slot, price, and promotion data. External LLM/RAG is optional and is not claimed. |

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

- PostgreSQL schema changes are Flyway-owned through V15; Hibernate uses
  `ddl-auto=validate` for PostgreSQL.
- H2 remains disposable for local automated tests.
- USD is the display and PayPal settlement currency.
- Booking state transitions and slot uniqueness are server-enforced.
- Image upload accepts JPEG/PNG/WebP/GIF up to 5 MB.
- No production/live-money PayPal certification is claimed.
