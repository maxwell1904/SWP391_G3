# AnPTT QA guide — final UC-50 to UC-63

This guide follows the final renumbered backlog. Use the Supabase-backed local
runtime so verification runs against the same Flyway-managed schema as the
classroom environment.

## Start locally

Open two PowerShell windows at the repository root.

```powershell
# Window 1
.\scripts\run-backend.ps1 -Local

# Window 2
cd frontend
npm run dev
```

Open `http://localhost:5173`. All seeded accounts use password
`GoalZone@123`:

- Customer: `customer@goalzone.local`
- Member: `member@goalzone.local`
- Staff: `staff@goalzone.local`
- Admin: `admin@goalzone.local`

## UC-51 — View active promotions

1. Stay signed out or sign in as Customer.
2. Open **Offers**.
3. Confirm only campaigns that are active, within their date range, and below
   their usage limit are visible.
4. If a campaign has a banner, confirm it is displayed with its conditions.

Demo line: “Guests only see promotions they can still use.”

## UC-52 — Manage promotion campaigns

1. Sign in as Admin and open **Offers**.
2. Choose **Add promotion**.
3. Fill code, name, discount, date range, usage limit, banner, and optional
   field/service/membership/day/time conditions; save.
4. Edit the campaign and change its status to inactive.
5. Sign out and check that the inactive campaign is no longer public.

Demo line: “Only Admin can create, update, enable, or disable campaigns.”

## UC-53 — Apply promotion to booking

1. Sign in as Customer and open **Book**.
2. Choose a future slot and enter an eligible promotion code.
3. Confirm the checkout preview changes its promotion discount and total.
4. Try a code with an incompatible field, time, service, membership, or date;
   confirm the server rejects it.
5. Complete the booking and open its billing detail to show the promotion
   snapshot.

Demo line: “The server validates conditions against the actual booking slot,
not just the screen input.”

## UC-54 — View membership progress

1. Sign in as Customer and open **Membership**.
2. Show the current tier, completed-booking count, current discount, and next
   tier progress.
3. Complete a checked-in booking as Staff and refresh Membership.
4. Confirm cancelled and no-show bookings do not increase the count.

## UC-55 — Manage membership rules

1. Sign in as Admin and open **Membership**.
2. Add or edit a tier using one rule:
   - `lifetime`: total completed bookings;
   - `monthly`: completed bookings this month;
   - `weekly`: required bookings per week for a consecutive-week streak.
3. Set its discount, benefits, display order, and active status; save.
4. Return as Customer to confirm the active tier is visible.

Example: Silver after 4 lifetime completed bookings; Gold after 8; Platinum
after two completed bookings per week for two consecutive weeks.

## UC-56 — View membership benefits

1. Sign in as Customer or stay a Guest and open **Membership**.
2. Confirm active tiers, discount, rule, and benefit text are visible.
3. Confirm the customer’s current tier is highlighted after signing in.
4. Mark a tier inactive as Admin, refresh as Customer, and confirm it is not
   shown publicly.

## UC-57 — Booking confirmation notification

1. Create a booking as Customer.
2. Open the notification bell to show the creation notification.
3. Confirm the booking as Staff/Admin.
4. Refresh the bell and show the confirmation notification.

## UC-58 — Booking reminder notification

1. As Admin, set `notification.booking_reminder_hours` in **Admin console →
   Policies** to a suitable value such as `24`.
2. Create a pending/confirmed future booking inside that window.
3. Wait for the one-minute scheduler cycle, then refresh the notification bell.
4. Confirm a single “Booking reminder” appears; wait another cycle and confirm
   it is not duplicated.

## UC-59 — Cancellation and refund notification

1. Cancel an eligible booking as Customer/Staff and show the cancellation bell
   message.
2. Create a refund request from its payment detail.
3. Approve/reject/process it as Staff/Admin.
4. Refresh the Customer bell and show the refund-status notification.

## UC-60 — Revenue report

1. Sign in as Admin and open **Admin console**.
2. In **Report date range**, set From/To then select **Apply range**.
3. Show net revenue, gross collected, refunds, field value, service value, and
   promotion/membership discounts.
4. Select **All time** to remove the date filter.

## UC-61 — Booking report

1. Stay in the Admin overview.
2. Show booking count, completed/cancelled/no-show totals, booking status
   breakdown, peak start times, and field utilization.
3. Change the date range to verify that these figures follow slot date.

## UC-61 — Customer activity report

1. Stay in the Admin overview.
2. Show returning customer count, top customers by booking count, and
   membership distribution.

## UC-62 — Ask availability assistant

1. Open **Find a field**.
2. Sign in as a Customer and ask a natural-language question.
3. With `GEMINI_API_KEY` configured, Gemini extracts the date, time, field
   type, and budget and phrases a short answer.
4. Explain that Gemini cannot invent availability: fields, prices, times, and
   eligible promotion codes are always returned by GoalZone's live UC-63
   ranking service.

## UC-63 — Suggested available slots

1. Use **Find best slots** or continue from the UC-62 answer.
2. Show that results are ranked and each has an explanation, price, and any
   usable promotion code.
3. Select **Book this slot** to continue into the normal booking flow.

## Automated regression

```powershell
cd backend
& 'C:\Program Files\NetBeans-13\netbeans\java\maven\bin\mvn.cmd' clean test

cd ..\frontend
npm run build
```

Expected: backend tests pass and the Vite production build succeeds.

## Shared Supabase deployment note

The shared Supabase schema is Flyway-managed and currently validated at V18.
Do not apply individual SQL files manually; start the backend with the
PostgreSQL profile so Flyway can validate and migrate in order.
