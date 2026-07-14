# GoalZone end-to-end QA matrix

Run date: 2026-07-14.  The automated suite uses the real Spring Security,
controllers, services and persistence flow against isolated H2 data.  The UI
smoke checks use the Customer, Staff and Admin workspaces in a real browser.

## Evidence

- `backend/src/test/java/com/swp391/backend/BacklogEndToEndApiTest.java`: five
  authenticated API journeys covering customer checkout/refunds, staff
  operations, scheduler policies, admin configuration, and account recovery.
- `backend/src/test/java/com/swp391/backend/service/*Test.java`: pricing,
  payment, reschedule, and refund regression cases.
- Browser smoke: Customer booking/account, Staff booking/activity, and Admin
  overview/fields/pricing/policies.  This caught and fixed the blank Staff
  booking list and the duplicate persistent success toast.
- Supabase: `scripts/verify-supabase-schema.sh` passes after Flyway V5.

## Backlog verdict

| UC | Verdict | Evidence / delivered behaviour |
| --- | --- | --- |
| UC-01 | Pass | Register, unique account validation, and customer default role are exercised end-to-end. |
| UC-02 | Pass | Customer, Staff, and Admin sign-in were tested through both API and browser. |
| UC-03 | Pass | Logout revokes the JWT; a revoked token is rejected. |
| UC-04 | Pass | Customer profile update is API-covered and exposed in the Account workspace. |
| UC-05 | Pass | Current-password validation and password change are E2E-covered. |
| UC-06 | Pass* | Reset-token lifecycle is E2E-covered; actual email delivery is a final external smoke check. |
| UC-07 | Pass | Admin can list, edit, lock/unlock, and inspect customer accounts. |
| UC-08 | Pass | Admin creates, updates, locks, and unlocks Staff accounts. |
| UC-09 | Pass | Staff customer-activity API and selected-booking UI panel are covered. |
| UC-10 | Pass | Booking restriction/restoration is separate from account lock and is E2E-covered. |
| UC-11 | Pass | Guest/customer field list is rendered from the live catalogue API. |
| UC-12 | Pass | Field detail cards expose type, price, location, surface, image, and availability. |
| UC-13 | Pass | Date/type slot search was browser-smoked with booked and free slots correctly distinguished. |
| UC-14 | Pass | Admin field create/update/status flow is E2E-covered. |
| UC-15 | Pass | Admin time/day pricing create/update is E2E-covered and visible in UI. |
| UC-16 | Pass | Staff block/unblock slot flow is E2E-covered. |
| UC-17 | Pass | Staff operation calendar by date is E2E-covered. |
| UC-18 | Pass | Admin extra-service create/update/status flow is E2E-covered. |
| UC-19 | Pass | Checkout validates active services and stock/max-per-booking rules. |
| UC-20 | Pass | Customer checkout and Staff walk-in booking include services in totals/invoice. |
| UC-21 | Pass | Staff service edit before check-in recalculates services/discounts/balance/refund delta and upserts the invoice in the same transaction; post-check-in editing is blocked. |
| UC-22 | Pass | Issue reporting is E2E-covered. |
| UC-23 | Pass | Staff assignment/resolution and reporter notification are E2E-covered. |
| UC-24 | Pass | Online booking with availability, pricing, hold, and payment flow is E2E-covered. |
| UC-25 | Pass | Staff walk-in booking and cash payment flow is E2E-covered. |
| UC-26 | Pass | Role/ownership-protected booking detail is used throughout the E2E journeys. |
| UC-27 | Pass | Customer workspace displays current bookings and payment detail. |
| UC-28 | Pass | Staff booking calendar is browser-smoked and API-covered. |
| UC-29 | Pass | Valid payment confirms a pending booking; Staff confirm control is present. |
| UC-30 | Pass | Staff rejects a pending online booking in the E2E suite. |
| UC-31 | Pass | Customer reschedule recalculates price while preserving paid amount and exposing balance/refund delta. |
| UC-32 | Pass | Cancellation preview is API-covered and is a deliberate first step in UI. |
| UC-33 | Pass | Customer cancellation is E2E-covered; checked-in/completed cancellation is blocked. |
| UC-34 | Pass | Staff check-in is E2E-covered. |
| UC-35 | Pass | Staff completion is E2E-covered. |
| UC-36 | Pass | Paid walk-in no-show is E2E-covered. |
| UC-37 | Pass | Reschedule/cancel/refund/notification paths provide the Staff conflict resolution tools. |
| UC-38 | Pass | Checkout summary exposes field, services, discounts, total, deposit, and balance. |
| UC-39 | Pass | Customer chooses deposit/full for online checkout; Staff/Admin record only cash at the venue. Both paths are API-covered. |
| UC-40 | Pass* | PayPal order/capture is customer-owned and online-booking-only; Staff/Admin attempts are denied by API tests. Real sandbox config is enabled. |
| UC-41 | Pass | Capture stores provider identifiers/status and updates payment/booking/invoice state. |
| UC-42 | Pass | Staff captures a remaining cash balance in the E2E suite. |
| UC-43 | Pass | Scheduler expiry of an aged unpaid hold is E2E-covered. |
| UC-44 | Pass | Customer payment history now returns actual captured transactions and was browser-smoked. |
| UC-45 | Pass | Invoice generation and its component amounts are asserted in payment journeys. |
| UC-46 | Pass | Booking billing panel exposes invoice and payment status. |
| UC-47 | Pass* | Refund request/approval/completion flow is E2E-covered; provider-side settlement remains sandbox-manual. |
| UC-48 | Pass | Staff refund list and explicit status transitions are E2E-covered. |
| UC-49 | Pass | Deposit setting update is E2E-covered and exposed in Policies. |
| UC-50 | Pass | Refund policy update is E2E-covered and exposed in Policies. |
| UC-51 | Pass | Active promotions are visible to guests/customers. |
| UC-52 | Pass | Admin promotion create/update/status path is E2E-covered. |
| UC-53 | Pass | Promotion validation and discount application are asserted in checkout preview. |
| UC-54 | Pass | Customer membership progress is E2E-covered and shown in Account. |
| UC-55 | Pass | Admin membership-level create/update is E2E-covered. |
| UC-56 | Pass | Membership benefits are exposed from active levels in customer-facing UI. |
| UC-57 | Pass | Booking-created/confirmed in-app notifications are generated and listed. |
| UC-58 | Pass | Scheduler creates exactly one upcoming-booking reminder; E2E-covered. |
| UC-59 | Pass | Cancellation/refund notification paths are E2E-covered. |
| UC-60 | Pass | Revenue/discount/refund metrics are returned in the Admin report. |
| UC-61 | Pass | Booking status, peak time, and field utilization metrics are returned in the Admin report. |
| UC-62 | Pass | Returning customers, top customers, and membership distribution are returned in the Admin report. |
| UC-63 | Deferred by scope | Smart assistant is intentionally not shipped; no fake/demo UI is left in the product. |
| UC-64 | Removed by scope | Suggested-slot feature is removed from route, UI, API, and backlog implementation. |

`*` A real external provider must still be approved from a PayPal Sandbox buyer
account / received in an actual inbox.  The configured credentials were
validated without exposing secrets, but this QA run intentionally did not send
mail or create a real provider-side transaction.

## Backlog cleanup note: UC-07 vs UC-10

They should not be identical in the final document.  Keep UC-07 as **customer
directory/account administration** (edit profile and account state) and UC-10
as **booking restriction** (a customer may still log in but cannot create a
new booking).  The UI and API implement both separately.

## Supabase result

The live database had Flyway history through V4 but lacked the indexes and
constraints expected by the code.  V5 repairs that legacy drift idempotently,
adds the provider/auth columns if absent, restores the booking/refund integrity
trigger, and creates the access-path indexes.  The database passes
`scripts/verify-supabase-schema.sh` after the repair.  PostgreSQL is now
protected from unsafe Hibernate DDL modes; it must run via the `postgres`
profile with Flyway and `ddl-auto=validate`.
