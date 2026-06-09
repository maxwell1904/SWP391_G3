# Implemented Use Cases

Last updated: 2026-06-03

Source of truth: `../SWP391_G3_Backlog.xlsx`, sheet `Backlog`. The `Contribution` sheet has one owner/module mismatch for BaoNG, so the UC IDs below follow the `Backlog` sheet.

Status meanings:

- Done: implemented end-to-end enough for demo.
- Partial: useful demo behavior exists, but the UC is not complete.
- Backend only: API/model behavior exists, but the React UI does not expose it clearly yet.
- Not implemented: not coded in the current demo slice.
- Extension: implemented behavior that supports the product but is not a numbered UC in the Excel backlog.

## BonVT - Account and Customer Status

| UC | Backlog use case | Current status | Notes |
| --- | --- | --- | --- |
| UC-01 | Register account | Done | Registration validates unique email/phone and password strength. SMTP email verification is implemented as an extension to registration. |
| UC-02 | Login | Done for demo | Login accepts email or phone and validates active account status. JWT/session invalidation is still future work. |
| UC-03 | Logout | Done for demo | React clears the local demo session and returns the user to the public app flow. |
| UC-04 | Manage personal profile | Done | Account page supports profile view/update for name, phone, and address. Backend validates phone format and uniqueness. |
| UC-05 | Change password | Not implemented | No authenticated change-password flow yet. |
| UC-06 | Forgot/reset password | Not implemented | No reset-token email flow yet. |
| UC-07 | Manage customer accounts | Partial | Admin can list customers and restrict/restore booking access. Full customer edit/lock/delete management is not implemented. |
| UC-08 | Manage staff accounts | Not implemented | Staff CRUD is not exposed yet. |
| UC-09 | View customer activity status | Partial | Activity is visible through bookings, membership, and reports. No dedicated customer-activity screen yet. |
| UC-10 | Restrict/unrestrict customer booking ability | Done | Admin UI and backend endpoint update restriction flags, and booking creation blocks restricted customers. |

## BaoNG - Field, Slot, Service, and Issue

| UC | Backlog use case | Current status | Notes |
| --- | --- | --- | --- |
| UC-11 | View field list | Done | Public field list is available in React and REST API. |
| UC-12 | View field detail | Partial | Backend detail exists; the current UI is list-focused and has no separate detail/review section. |
| UC-13 | Search available fields by date/time | Done for demo | Slot search filters by date and field type, removes booked/blocked/past slots, and refreshes pricing automatically. |
| UC-14 | Manage football fields | Not implemented | Field CRUD is not exposed yet. |
| UC-15 | Manage field pricing by time range | Not implemented | Pricing is seeded/modelled, but there is no management UI/API flow. |
| UC-16 | Manage unavailable slots | Backend only | Slot blocking endpoint exists. Staff UI for blocking/unblocking slots is still missing. |
| UC-17 | View field operation calendar | Partial | Staff can view booking operations by list/date context. A full field/date calendar is not implemented. |
| UC-18 | Manage extra services | Partial | Services are seeded/listed and used in booking. Extra-service CRUD is not implemented. |
| UC-19 | Check extra service availability | Done | Booking validates requested service quantity against max/stock rules. |
| UC-20 | Add extra services to booking | Done | Services can be selected before booking and are priced in checkout/invoice snapshots. |
| UC-21 | Update extra services before check-in | Not implemented | No post-booking service edit flow yet. |
| UC-22 | Report field/service issue | Done | Staff/customer issue creation and issue listing are available for demo. |
| UC-23 | Resolve field/service issue | Not implemented | Issue status/resolution fields exist in schema/entity, but no resolve workflow is exposed yet. |

## NgocPA - Booking Lifecycle

| UC | Backlog use case | Current status | Notes |
| --- | --- | --- | --- |
| UC-24 | Create online booking | Done for demo | Customer creates pending online booking after email verification and conflict checks. |
| UC-25 | Create walk-in booking | Done | Staff/Admin can create walk-in booking for an existing customer without membership discount. |
| UC-26 | View booking detail | Partial | Backend detail includes services, payments, promotions, invoice, and refunds. Frontend mostly shows summaries/lists. |
| UC-27 | View my bookings | Done | Customer account page lists that customer's bookings. |
| UC-28 | View booking calendar | Partial | Staff operation list supports daily work, but not a full calendar UI by field/date. |
| UC-29 | Confirm booking | Done | Staff/Admin can confirm bookings; sandbox payment can also confirm pending bookings after deposit capture. |
| UC-30 | Reject booking request | Done for demo | Staff/Admin can reject pending bookings and the customer receives a notification. |
| UC-31 | Reschedule booking | Not implemented | No slot-change/reschedule flow yet. |
| UC-32 | Preview cancellation fee/refund | Partial | Cancellation calculation is stored when cancelling. Dedicated preview-before-confirm UI/API is still missing. |
| UC-33 | Cancel booking | Done | Staff/Admin can cancel eligible bookings and trigger cancellation notification/refund calculation. |
| UC-34 | Check-in booking | Done | Confirmed bookings can move to checked-in. |
| UC-35 | Complete booking | Done | Checked-in bookings can complete and update membership progress/invoice. |
| UC-36 | Mark no-show | Done | Confirmed bookings can be marked no-show. |
| UC-37 | Handle booking conflict | Partial | Active-slot conflict checks prevent double booking. No dedicated manual conflict-resolution workflow yet. |

## AnNP - Payment, Refund, and Invoice

| UC | Backlog use case | Current status | Notes |
| --- | --- | --- | --- |
| UC-38 | View checkout summary | Done | Checkout summary updates automatically when slot, services, promotion, or customer context changes. |
| UC-39 | Choose payment option | Partial | Backend supports deposit/full/remaining options. The customer-facing UI does not expose a full payment-option selector yet. |
| UC-40 | Pay deposit/full amount via online payment sandbox | Partial | Deterministic `online_sandbox` payment records exist. No real PayPal redirect/callback is wired yet. |
| UC-41 | Capture/confirm online payment | Done for sandbox | Payment capture stores status/transaction code and can confirm pending booking after deposit is paid. |
| UC-42 | Confirm remaining payment | Done | Staff/Admin can capture remaining payment, usually as cash for venue operation demo. |
| UC-43 | Handle failed/expired payment | Partial | Failed payments can be stored. Expiry timer/job and UI handling are not implemented. |
| UC-44 | View payment history | Done | Payment history is shown in account/staff/admin data flows. |
| UC-45 | Generate booking invoice | Done | Invoice summary is generated/updated after payment capture and completion. |
| UC-46 | View invoice/payment status | Partial | Payment status is visible and booking detail API returns invoice. No dedicated invoice detail page yet. |
| UC-47 | Process online refund | Partial | Refund records and completed refund transaction codes exist. No real gateway refund call. |
| UC-48 | Manage refund requests | Partial | Staff/Admin can process a refund demo case. A full request queue with approve/reject states is missing. |
| UC-49 | Configure deposit rules | Partial | Admin can switch deposit percent between demo values through `system_setting`. Full rule editor is not implemented. |
| UC-50 | Configure cancellation/refund policy | Partial | Policy values are modelled in `system_setting`; full admin policy UI/rule editor is not implemented. |

## AnPTT - Promotion, Membership, Notification, and Report

| UC | Backlog use case | Current status | Notes |
| --- | --- | --- | --- |
| UC-51 | View active promotions | Done | Public promotions are listed in the React app. |
| UC-52 | Manage promotion campaigns | Not implemented | Promotion CRUD is not exposed yet. |
| UC-53 | Apply promotion to booking | Done | Promotion preview validates active dates, usage limit, minimum amount, field type, service condition, and snapshots the applied discount. |
| UC-54 | View membership progress | Done | Customer account shows current level, completed bookings, discount, and next-level progress. |
| UC-55 | Manage membership rules | Not implemented | Membership levels are seeded/modelled but not editable in UI. |
| UC-56 | View membership benefits | Partial | Membership level data exists and progress shows current benefit. No separate public benefits page yet. |
| UC-57 | Send booking confirmation notification | Done | Confirmation and payment flows create in-app notifications. |
| UC-58 | Send booking reminder notification | Not implemented | No scheduled reminder job/UI yet. |
| UC-59 | Send cancellation/refund notification | Done for demo | Cancellation and refund completion create in-app notifications. |
| UC-60 | View revenue report | Done for demo | Admin sees paid revenue and booking counts. |
| UC-61 | View booking report | Partial | Admin sees status counts and field utilization. More detailed trends/peak slots are missing. |
| UC-62 | View customer activity report | Partial | Admin report includes top customers by booking count, but no dedicated customer activity report page. |
| UC-63 | Ask smart assistant for available fields | Not implemented | Optional backlog item; intentionally not modelled yet. |
| UC-64 | Get suggested available slots | Not implemented | Optional backlog item; intentionally not modelled yet. |

## Extensions Not Counted As Excel UCs

| Extension | Status | Notes |
| --- | --- | --- |
| SMTP email verification link | Done | Implemented as a registration extension. Verification link is sent to the registered email address. |
| Password visibility and strength validation | Done | Login/register forms have show-password controls and register enforces a password policy. |
| Supabase-ready configuration | Done for setup | Runtime env supports Supabase PostgreSQL through `.env.local`; local demo can still run with H2. |
| React feature folder split | Done | Frontend is split into app, components, features, pages, services, styles, and utils. |

## Notes For Demo

- The app must not display task owner names or implementation comments in the UI.
- The app starts on a public home/field browsing experience. Login is available from the header.
- Use `docs/BACKLOG_RDS_CODE_GAP_ANALYSIS.md` for the current mismatch list before finalizing the RDS.
