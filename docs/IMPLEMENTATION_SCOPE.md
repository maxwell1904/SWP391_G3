# Implementation Scope

This repo is a runnable MVP slice for the Football Field Booking System.

## Backlog Coverage

The implemented demo scope covers account access, local email verification, field/slot search, service add-ons, booking lifecycle, checkout/payment records, refund, promotions, membership progress, notifications, and basic reports. Per project convention, member-to-use-case ownership is kept as code comments in the backend classes only.

## Intentional Exclusions

- No review table or review UI. The backlog mention in field detail should be removed unless the team adds review use cases.
- No real payment gateway integration. The payment sandbox is deterministic and stores transaction codes.
- No real SMTP integration. Email verification is a local demo code flow until mail provider credentials are available.
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
