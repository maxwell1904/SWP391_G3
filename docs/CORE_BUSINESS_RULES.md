# GoalZone core business rules

Status: canonical retake baseline, 2026-08-01.

This file is the short answer when code, backlog, RDS, SDS, or database wording appears to disagree. A change is complete only after every affected artifact is updated.

## Feature versus use case

- A **feature** is a stable capability group, such as Authentication, Booking creation, Issue management, or Reporting.
- A **use case** is one actor goal with an observable result, such as UC-02 Login or UC-22 Report field/service issue.
- A validation, database write, notification, button, or provider callback is normally a step inside a use case, not a separate use case.
- Booking-conflict handling composes UC-22 issue reporting, UC-29 rescheduling, UC-30 cancellation, refund, and notification behavior; it is not a detached use case or table.

## Actor responsibility

| Actor | Owns | Must not do |
| --- | --- | --- |
| Guest | Browse fields, prices, promotions, benefits, and deterministic availability | Receive personalized membership pricing |
| Customer | Own profile, online booking, PayPal Sandbox, own cancellation/reschedule/add-ons/issues/refunds | Operate another Customer's data or submit Staff actions |
| Venue Staff | Walk-in booking, operational slot blocks, daily operations, cash, check-in/completion/no-show, issue handling | Configure venue policy or impersonate another Staff identity |
| Admin | Accounts, field/pricing/service/calendar/policy/promotion/membership configuration, audit and reports | Perform walk-in, counter-cash, check-in/completion/no-show, or issue-handling actions |
| Scheduler / external services | Expire unpaid holds, send reminders, return PayPal/SMTP results | Impersonate a human actor or bypass GoalZone business rules |

Every mutation derives the acting user from the authenticated JWT. Request DTOs do not accept `staffId`, `reporterId`, `createdById`, `processedById`, or `updatedById`.

For walk-in booking, Staff first searches a counter-safe list of active Customers by phone or email. If no account matches, Staff records a first-time visitor's name and phone (email optional) directly on the booking. This does not create an account or grant Customer administration authority. Guest walk-ins have no membership discount, PayPal checkout, or account notification.

## Presentation identifiers

- Numeric primary keys remain internal API/database keys.
- Users see business references where a reference is useful: `bookingCode`, `paymentCode`, `invoiceCode`, and `refundCode`.
- Issue screens show title, description, booking/field context, status, created time, assignee, and resolution. A raw `issueId` is not presented as customer information.

## Booking lifecycle and operational time

```text
pending -> confirmed (payment) | cancelled (Customer/Staff) | expired (scheduler)
confirmed -> checked_in | cancelled | no_show
checked_in -> completed
terminal -> no further transitions
```

- Confirmation requires the configured deposit to have been paid and is performed by the valid payment workflow. Staff cannot manually approve or reject an unpaid online request.
- Cancellation, rescheduling, and add-on edits must occur before the booked start.
- Availability search and field-detail suggestions omit slots whose start time has passed; checkout preview and booking creation revalidate the same rule on the server.
- Check-in opens 30 minutes before start and requires the deposit.
- No-show opens 15 minutes after start.
- Completion requires check-in, the start time to have passed, and zero remaining balance.
- Active slot reservation statuses are `pending`, `confirmed`, and `checked_in`.

## Pricing, promotion, and inventory

- A slot is bookable only when an active field price matches its field, date/effective period, day type, and entire time range. There is no magic fallback price.
- Add-on quantities are positive integers. Duplicate service selections, inactive services, per-booking limits, and overlapping stock limits are rejected.
- One promotion code is snapshotted per booking. `stackable` controls whether membership discount may apply after it.
- A pending booking temporarily reserves campaign capacity. The reservation remains counted after confirmation and is released by cancellation, rejection, or expiry.

## Payment, refund, and invoice

- PayPal Sandbox is an intentional external demo boundary; it is not a problem or a live-money claim.
- Customer PayPal and Venue Staff cash are separate paths. Admin has commercial audit authority but no counter-cash, customer-checkout, or refund-processing authority.
- Provider `COMPLETED`, exact USD amount, provider identifiers, and a stable idempotency key are required before a PayPal capture changes the local ledger.
- `paidAmount` is gross collected money. Refunds, provider fee, provider net, and remaining balance stay separate and auditable.
- Refund reason is required; only the booking owner requests, Venue Staff reviews/processes, and Admin may inspect the result for audit.

## Issue and reporting rules

- Customer or Venue Staff may report; title and description are required and linked booking/field/service data must agree.
- Issue flow is `open -> in_progress/resolved/rejected` and `in_progress -> resolved/rejected`; closed issues cannot reopen.
- Revenue excludes cancelled/rejected/expired booked value. Utilization counts confirmed/checked-in/completed/no-show only. Customer rankings respect the selected report period.

## Database source of truth

- PostgreSQL/Supabase schema changes belong in Flyway migrations; current version is **V20**.
- Hibernate entity mappings must match the migrated schema. Repository/JPQL/native-query code controls application queries; changing an entity alone is not a production schema migration.
- V20 adds `booking_promotion.usage_counted` and reconciles `promotion.used_count` with active promotion reservations.
