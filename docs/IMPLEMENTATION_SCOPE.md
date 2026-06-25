# Implementation Scope

This repo is a runnable MVP slice for the Football Field Booking System.

## Backlog Coverage

The implemented demo scope covers account access, SMTP email verification as a registration extension, field/slot search, service add-ons, booking lifecycle, checkout/payment records, refund, promotions, membership progress, notifications, and basic reports.

Use `docs/IMPLEMENTED_USE_CASES.md` for the backlog-aligned UC status table. Use `docs/BACKLOG_RDS_CODE_GAP_ANALYSIS.md` before finalizing the RDS because several manage/CRUD and workflow use cases are still partial or not implemented.

Member-to-use-case ownership is documented in project docs only. Do not render owner names in the application UI.

## Intentional Exclusions

- No review table or review UI. The backlog mention in field detail should be removed unless the team adds review use cases.
- No real payment gateway integration. The payment sandbox is deterministic and stores transaction codes.
- Email verification uses Spring Boot Mail SMTP and sends verification links to the registered email address. SMTP credentials stay in `.env.local`.
- No AI assistant or suggested slot engine. UC-63 and UC-64 stay optional.
- No inventory transaction ledger. Extra service availability is kept at service-level stock/max quantity for MVP.

## Run

Backend:

```bash
scripts/run-backend.sh
```

Frontend:

```bash
scripts/run-frontend.sh
```

Open `http://localhost:5173`.
