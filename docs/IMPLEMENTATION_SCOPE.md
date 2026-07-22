# Implementation Scope

> Archived scope snapshot. Use `BACKLOG_CODE_FIRST_DELIVERY.md` for the
> current code-first decisions.

This repo is a runnable MVP slice for the Football Field Booking System.

## Backlog Coverage

The implemented demo scope covers account access, SMTP email verification as a registration extension, field/slot search, service add-ons, booking lifecycle, checkout/payment records, refund, promotions, membership progress, notifications, and basic reports.

Use `docs/BACKLOG_CODE_FIRST_DELIVERY.md`, `docs/END_TO_END_QA_MATRIX.md`, and `docs/RDS_SDS_ALIGNMENT_AUDIT.md` for current status. `IMPLEMENTED_USE_CASES.md` and `BACKLOG_RDS_CODE_GAP_ANALYSIS.md` are archived snapshots from before completion.

Member-to-use-case ownership is documented in project docs only. Do not render owner names in the application UI.

## Intentional Exclusions

- No review table or review UI. The backlog mention in field detail should be removed unless the team adds review use cases.
- No real payment gateway integration. The payment sandbox is deterministic and stores transaction codes.
- Email verification uses Spring Boot Mail SMTP and sends verification links to the registered email address. SMTP credentials stay in `.env.local`.
- UC-63 and UC-64 are implemented as an explainable rule-based availability assistant using live slot, price, membership, and promotion data. External AI/LLM integration remains optional.
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
