# GoalZone end-to-end QA matrix

Run date: 2026-08-01

Latest run: backend 21 tests with 0 failures/errors/skips; frontend Vite production build passed; browser smoke checks passed.

## Automated gates

| Area | Required evidence |
| --- | --- |
| Account/access | Registration/login/logout/recovery; self-only profile; separate Admin Customer View and Lock/Unlock; Staff invitation |
| Fields/slots | Active catalogue; priced search without fallback; Admin slot-rule validation; Staff block conflict guard; operation calendar |
| Services/issues | Add/update service validation and repricing; authenticated reporter identity; guarded issue transitions |
| Booking | Online/walk-in create; ownership/roles; no manual approval queue; reschedule/cancel timing; check-in/completion/no-show gates |
| Payment/refund | PayPal Sandbox and Staff cash isolation; idempotent provider capture/refund; payment expiry; invoice reconciliation |
| Promotion/membership | Eligibility, caps, stacking, pending reservation/release, configured progress rules |
| Notifications/reports | De-duplicated reminder; cancellation/refund notice; period-correct revenue/booking/customer reports |
| PostgreSQL | Flyway V1–V21 validates; DB verifier passes; 19 JPA/DBML/ERD tables agree |
| Frontend | Production build and browser smoke checks for Guest, Customer, Staff, and Admin navigation |
| Documents | 56 class + 56 sequence diagrams pass audit; final DOCX/PDF page render passes |

## Final scope matrix

| UC range | Owner area | Verdict |
| --- | --- | --- |
| UC-01–UC-10 | Account and access | Covered |
| UC-11–UC-23 | Fields, slots, services, and issues | Covered |
| UC-24–UC-33 | Booking and venue operations | Covered |
| UC-34–UC-44 | Checkout, payments, refunds, and policy | Covered |
| UC-45–UC-56 | Promotions, membership, notifications, and reports | Covered |

## Explicit negative checks

- `/assistant` is not a route and assistant/suggestion APIs are absent.
- Staff attempts to set a pending booking to `confirmed` or `rejected` return 400.
- Unpriced slots do not become bookable through a fallback.
- Customer/Admin cannot execute Venue Staff operational actions.
- Browser success notifications dismiss automatically and raw issue IDs are not presented as meaningful content.

## Manual external checks

- Confirm one verification/recovery/invitation email using the configured SMTP mailbox.
- Complete one PayPal Sandbox buyer approval; optionally demonstrate an approved provider refund.
- External credentials remain in `.env.local`, never in the submitted repository.
