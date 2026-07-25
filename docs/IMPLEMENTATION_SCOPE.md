# Implementation Scope

> Archived scope snapshot. Use `BACKLOG_CODE_FIRST_DELIVERY.md` for the
> current code-first decisions.

This repo is the runnable classroom release of the Football Field Booking System.

## Backlog Coverage

The implemented classroom scope covers account access, SMTP email verification/invitations, field/slot search, service add-ons, booking lifecycle, checkout/payment records, real PayPal Sandbox refund processing, promotions, membership progress, notifications, and operational reports.

Use `docs/BACKLOG_CODE_FIRST_DELIVERY.md`, `docs/END_TO_END_QA_MATRIX.md`, and `docs/RDS_SDS_ALIGNMENT_AUDIT.md` for current status. `IMPLEMENTED_USE_CASES.md` and `BACKLOG_RDS_CODE_GAP_ANALYSIS.md` are archived snapshots from before completion.

Member-to-use-case ownership is documented in project docs only. Do not render owner names in the application UI.

## Intentional Exclusions

- No review table or review UI. The backlog mention in field detail should be removed unless the team adds review use cases.
- Customer online payments and approved refunds use real PayPal Sandbox Orders/Payments APIs. Staff walk-ins use recorded cash transactions.
- Email verification uses Spring Boot Mail SMTP and sends verification links to the registered email address. SMTP credentials stay in `.env.local`.
- UC-62 has a grounded Gemini integration but still needs an API key in the
  deployment environment. UC-63 independently ranks live slot, price,
  membership, and promotion data and feeds the ordinary booking flow.
- No inventory transaction ledger. Extra-service availability is kept at service-level stock and maximum quantity per booking.

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
