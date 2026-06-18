# Backlog, RDS, and Code Gap Analysis

Last updated: 2026-06-11

## Source Files Checked

- Backlog: `../SWP391_G3_Backlog.xlsx`, sheet `Backlog`
- Current RDS notes: `../output/rds/READY_FOR_RDS.md`
- Current ERD schema: `database/football_field_booking_slim.dbml`
- Current codebase: Spring Boot backend and React frontend in this repository

## Source-Of-Truth Decision

Use the `Backlog` sheet as the UC source of truth. The `Contribution` sheet currently has a module/owner mismatch for BaoNG, while the detailed UC rows in `Backlog` are internally consistent.

## Corrections Already Made

- Recreated `docs/IMPLEMENTED_USE_CASES.md` because the previous file mapped several UC IDs incorrectly.
- Updated `database/football_field_booking_slim.dbml` so `app_user` includes email verification fields used by the code-first entity.
- Added a PostgreSQL implementation index note for `app_user.email_verification_token`.
- Added profile editing UI/API support for UC-04.
- Added Admin customer restrict/restore UI support for UC-07/UC-10.
- Added staff reject-booking support for UC-30 with pending-status guard and customer notification.
- Completed UC-39 with deposit/full selection in checkout and server-side payable amount validation.
- Completed UC-46 demo coverage with invoice/payment detail panels for customer and staff workspaces.

## Main Mismatches

### 1. Old UC Documentation Did Not Match The Excel Backlog

The deleted `docs/IMPLEMENTED_USE_CASES.md` treated email verification as UC-04, but the backlog says UC-04 is `Manage personal profile`. It also shifted several Booking, Payment, and Notification/Report UC IDs.

Current fix: the new `docs/IMPLEMENTED_USE_CASES.md` follows the Excel `UC ID` column exactly.

### 2. Email Verification Is A Registration Extension, Not A Numbered UC

The code now sends SMTP verification links to the registered email address. This is good product behavior, but the backlog does not assign it a separate UC ID.

RDS recommendation: describe email verification under UC-01 registration rules, not as a standalone UC unless the team officially adds it.

### 3. RDS Field Detail Mentions Reviews, But Backlog/Code Do Not

Current RDS notes say field detail may include reviews, but there is no review UC, table, entity, API, or UI.

RDS recommendation: remove review text from field detail unless the team adds a review UC and schema.

### 4. Schema Was Behind Code For Email Verification

`AppUser` has `email_verified`, `email_verification_token`, and `email_verification_sent_at`, but the DBML initially missed those fields.

Current fix: DBML and PostgreSQL notes now include those fields/index notes.

### 5. PayPal Sandbox Checkout Is Implemented

Customer checkout now loads the PayPal JavaScript SDK and uses server-side Orders v2 create/capture calls with sandbox credentials. Captured amount and currency are validated before the booking and invoice are updated. Cancelling the popup expires the local pending payment/booking and releases the slot.

Remaining production gaps are webhook reconciliation, scheduled payment expiry, and gateway-backed refunds.

### 6. Manage/CRUD Use Cases Are Mostly Not Implemented Yet

These UCs are modelled or seeded but do not have full management UI/API:

- UC-08 Manage staff accounts
- UC-14 Manage football fields
- UC-15 Manage field pricing by time range
- UC-18 Manage extra services
- UC-52 Manage promotion campaigns
- UC-55 Manage membership rules

RDS recommendation: mark them as future scope or implement the CRUD screens before claiming them.

### 7. Booking Lifecycle Still Has Missing Workflows

Implemented lifecycle coverage is enough for a basic demo, but these remain incomplete:

- UC-31 Reschedule booking
- UC-32 Dedicated cancellation fee/refund preview before cancellation
- UC-37 Manual booking conflict handling workflow

UC-30 reject booking is now demo-ready.

### 8. Slot/Service/Issue Operations Need More UI

- UC-16 has backend slot blocking, but no staff UI for unavailable-slot management.
- UC-21 has no update-extra-services-before-check-in flow.
- UC-23 has issue status/resolution fields in entity/schema, but no resolve workflow in UI/API.

RDS recommendation: avoid claiming these as done. They are good next candidates because the schema is already close.

### 9. Reports Are Basic

Revenue and booking counts exist. Field utilization and top customer counts exist. Dedicated customer activity report, trend charts, peak time analysis, and export are not implemented.

RDS recommendation: describe reports as MVP summaries unless the team builds richer reporting.

### 10. Deposit/Refund Configuration Is Partial

`system_setting` supports small runtime settings. Admin UI can switch deposit percent between demo values, but cancellation/refund policy configuration is not a full rule editor.

RDS recommendation: keep UC-49/UC-50 as partial/demo unless a richer settings screen is added.

## Suggested Next Coding Order

1. UC-23 issue resolution: add a small endpoint and staff UI buttons for `in_progress`, `resolved`, and `rejected`.
2. UC-16 unavailable slots UI: expose the existing slot block endpoint from Staff/Admin.
3. UC-32 cancellation preview: add preview API/UI before final cancellation.
4. UC-21 update extra services before check-in: edit service items while booking is pending/confirmed.
5. UC-31 reschedule booking: change slot with conflict validation and notification.
6. Admin CRUD pass: fields/prices/services/promotions/membership/staff accounts, only if the demo needs those management UCs.

## Current Demo Boundary

The codebase is a code-first MVP, not a full implementation of all 64 backlog UCs. The current valuable demo path is:

Guest browses fields/promotions -> customer registers/verifies email -> customer searches slots and creates online booking -> staff/admin confirms or rejects -> payment/refund/invoice records -> check-in/complete/no-show/cancel -> membership/notification/report summaries.
