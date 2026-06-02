# Implemented Use Cases

Last updated: 2026-06-01

This file tracks the demo-level use cases implemented in the codebase and keeps the backlog ownership visible for team reporting. Owner names are documentation/code-comment metadata only and are not shown in the application UI.

## Summary

- Frontend: React + Vite single-page app with routes for public browsing, booking, account, staff operation, and admin reporting.
- Backend: Spring Boot REST API with JPA entities, repositories, services, validation, seed data, and Supabase PostgreSQL support.
- Database: code-first JPA schema aligned with the slim ERD under `database/football_field_booking_slim.dbml`.
- Demo auth: local account login/register and local email verification-link flow. No real SMTP yet.
- Demo payment: deterministic `online_sandbox` payment records. No real PayPal redirect/callback yet.

## BonVT - Account And Customer Access

| UC | Status | Implemented value |
| --- | --- | --- |
| UC-01 Register Account | Done | Customer registration with unique email/phone validation, enforced password policy, generated email verification token, and default membership creation. |
| UC-02 Login | Done | Login by email or phone, role-aware frontend session, logout, and local session persistence for demo refresh/direct-route testing. |
| UC-04 Verify Email | Done as local demo | Verification link UI and backend verify/resend endpoints. The link is shown in the local demo instead of being delivered by SMTP. |
| UC-07 View Account Area | Done | Customer account page shows bookings, payments, membership progress, and notifications. |
| UC-10 Customer Booking Restriction | Done in backend | Restricted customers are blocked from creating bookings. Restriction admin UI can be added later if needed. |

Main files:
- `backend/src/main/java/com/swp391/backend/controller/AccountController.java`
- `backend/src/main/java/com/swp391/backend/entity/AppUser.java`
- `frontend/src/App.jsx`

## BaoNG - Field, Slot, Service, And Issue Operations

| UC | Status | Implemented value |
| --- | --- | --- |
| UC-11 Browse Fields | Done | Guest/customer field listing with field type, image, location, surface, and active status. |
| UC-13 Search Available Slots | Done | Date and field-type filtering, unavailable/booked/blocked slot handling, and price display. |
| UC-16 Field/Slot Availability | Done for demo | Seeded fields, prices, blocked slot, and staff-facing booking calendar context. Full CRUD screens can be added later. |
| UC-18 Add Extra Services | Done | Booking service add-ons with quantity, stock, and max-per-booking validation. |
| UC-22 Report Field/Service Issue | Done | Staff/customer issue creation with booking/field/service context and staff operation listing. |

Main files:
- `backend/src/main/java/com/swp391/backend/controller/FieldOperationController.java`
- `backend/src/main/java/com/swp391/backend/entity/FootballField.java`
- `backend/src/main/java/com/swp391/backend/entity/Slot.java`
- `backend/src/main/java/com/swp391/backend/entity/ExtraService.java`
- `backend/src/main/java/com/swp391/backend/entity/Issue.java`

## NgocPA - Booking Lifecycle

| UC | Status | Implemented value |
| --- | --- | --- |
| UC-24 Checkout Preview | Done | Live checkout preview with field price, services, promotion, membership, deposit, remaining amount, and total. |
| UC-25 Create Online Booking | Done | Online booking creation, email-verified customer guard, slot conflict guard, pending status, and notification. |
| UC-27 View Booking Calendar/List | Done | Booking list for account and staff operation views. |
| UC-28 Confirm Booking | Done | Staff/admin status transition to confirmed. |
| UC-29 Check In | Done | Staff/admin status transition to checked in. |
| UC-30 Complete Booking | Done | Completion updates membership progress based on completed bookings only. |
| UC-31 Cancel Booking | Done | Cancellation transition and notification. |
| UC-33 Mark No-show | Done | Staff/admin no-show transition. |
| UC-34 Walk-in Booking | Done | Staff/admin creates confirmed walk-in booking. Membership discount is not auto-applied to walk-in checkout. |
| UC-35/36 Booking Detail And History | Done for demo | Booking summaries include status, customer, field, payment amounts, discounts, and timestamps. |

Partial:
- Cancellation preview before confirming cancellation should be added as a focused UC-32 endpoint/UI.
- Reschedule flow is intentionally left for a later pass.

Main files:
- `backend/src/main/java/com/swp391/backend/controller/BookingController.java`
- `backend/src/main/java/com/swp391/backend/entity/Booking.java`
- `backend/src/main/java/com/swp391/backend/service/MvpDemoService.java`

## AnNP - Payment, Refund, And Invoice

| UC | Status | Implemented value |
| --- | --- | --- |
| UC-38 Capture Deposit/Payment | Done as sandbox record | Payment capture endpoint creates paid/failed records, transaction code, gateway message, and updates booking paid/remaining amounts. |
| UC-40 Generate Invoice | Done | Invoice summary is generated from booking totals, discounts, tax placeholder, paid amount, and remaining amount. |
| UC-44 Refund Request/Processing | Done for demo | Refund creation supports requested/processed users, amount, reason, approve-now path, and status. |
| UC-45 View Payment/Refund Records | Done | Account/staff/admin views consume payment and refund endpoints. |
| UC-47 Online Payment Gateway | Partial | Uses deterministic `online_sandbox`; real PayPal sandbox redirect/callback is intentionally not implemented yet. |
| UC-48 Remaining Payment | Done | Staff operation can capture remaining payment for selected booking. |

Main files:
- `backend/src/main/java/com/swp391/backend/controller/PaymentController.java`
- `backend/src/main/java/com/swp391/backend/entity/Payment.java`
- `backend/src/main/java/com/swp391/backend/entity/Refund.java`
- `backend/src/main/java/com/swp391/backend/entity/Invoice.java`

## AnPTT - Promotion, Membership, Notification, And Report

| UC | Status | Implemented value |
| --- | --- | --- |
| UC-51 View Promotions | Done | Public promotions list with active seeded promotions. |
| UC-53 Apply Promotion | Done | Promotion validation checks active dates, usage limit, minimum amount, field type, service condition, max discount, and applied snapshot. |
| UC-54 Membership Progress | Done | Membership levels, completed-booking count, discount percent, and next-level progress. Only completed bookings count. |
| UC-55 Revenue/Utilization Report | Done for demo | Admin report includes total paid revenue, booking counts, completion/cancellation counts, and field utilization. |
| UC-60 Notification Records | Done | Booking/payment/status events create notifications for customer account view. |
| UC-61 System Settings | Done for demo | Admin can update deposit percent setting. |
| UC-62 Deposit/Refund Rule Visibility | Partial | Settings exist for deposit and refund percentages; richer rule screens can be added later. |

Main files:
- `backend/src/main/java/com/swp391/backend/controller/PromotionReportController.java`
- `backend/src/main/java/com/swp391/backend/entity/Promotion.java`
- `backend/src/main/java/com/swp391/backend/entity/MembershipLevel.java`
- `backend/src/main/java/com/swp391/backend/entity/CustomerMembership.java`
- `backend/src/main/java/com/swp391/backend/entity/Notification.java`
- `backend/src/main/java/com/swp391/backend/entity/SystemSetting.java`

## Current Demo Login Accounts

All seeded accounts use password `GoalZone@123`.

| Role | Email |
| --- | --- |
| Customer | `customer@goalzone.local` |
| Member Customer | `member@goalzone.local` |
| Staff | `staff@goalzone.local` |
| Admin | `admin@goalzone.local` |

## Verification Notes

- Frontend build: `npm run build`
- Frontend audit: `npm audit --audit-level=high`
- Backend test: `../.tools/apache-maven-3.9.9/bin/mvn -q test`
- Supabase connection: `scripts/check-supabase.sh`
- Manual smoke tested: login/logout, route refresh, checkout preview, email verification, online booking, sandbox payment capture, slot conflict guard, staff route, admin route.
