# Code-first ERD decisions

The canonical editable ERD is `docs/ERD/football_field_booking_erd_code_first.drawio.xml`.
It follows the Java entities and PostgreSQL migrations through Flyway V11.

## Corrections made to the submitted ERD

- Renamed the physical `user` table to `app_user`, matching `AppUser` and avoiding
  a confusing SQL identifier.
- Added `system_setting`, which stores the deposit, payment timeout, refund, and
  reminder policies editable by administrators.
- Added the user relationships represented by `issue.assigned_staff_id`,
  `slot.created_by`, `payment.created_by`, `refund.requested_by`,
  `refund.processed_by`, and `system_setting.updated_by`.
- Corrected `app_user` to `customer_membership` to zero-or-one membership record
  per customer, as enforced by the unique `customer_id` constraint.
- Corrected slot history to allow multiple historical bookings for one slot while
  retaining at most one active booking through PostgreSQL's partial unique index.

## Why `field_type` and `field_price` remain separate

They should not be merged into `field`:

- `field_type` is reusable classification data (for example 5-a-side and
  7-a-side) with capacity and description. Many fields can share one type.
- `field_price` is a one-to-many schedule of effective pricing rules by day type,
  time range, and date range. A single price column on `field` cannot represent
  peak/off-peak or future price changes without duplicating field rows.

This normalization is appropriate for the current search, pricing, reporting,
and booking-reschedule logic.

## Physical database notes

- Monetary values use fixed-precision `numeric` columns and USD throughout.
- Payment gross amount, PayPal processor fee, provider net amount, refund amount,
  and cancellation fee are recorded separately. Revenue is gross collected minus
  completed refunds and tracked provider fees.
- Foreign-key indexes and the refund/issue work-queue indexes are present.
- The application uses server-side JDBC with Flyway and Hibernate validation;
  Supabase Row Level Security is therefore not part of the current access model.
  API authorization remains enforced by Spring Security and service-level
  ownership checks.
