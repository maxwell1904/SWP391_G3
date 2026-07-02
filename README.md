# SWP391_G3

Football Field Booking & Venue Operation System

## Tech Stack
- Backend: Spring Boot
- Frontend: React + Vite
- Database: H2 for local demo, Supabase PostgreSQL ready through env vars
- Email: Spring Boot Mail SMTP for account verification links
- Authentication: Demo login now, JWT dependency kept for the next security pass

## Project Structure
```text
SWP391_G3/
├── backend/
├── frontend/
├── database/
├── docs/
└── README.md
```

## Main Roles
- Guest
- Customer
- Staff
- Admin

## Main Modules
- Account & Authentication
- Field & Slot Management
- Booking Lifecycle
- Payment & Refund
- Promotion & Membership
- Notification & Reports

## Git Flow Suggestion
- main: stable branch
- develop: integration branch
- feature/<feature-name>: feature branches

## Team Convention
- Backend uses REST API.
- Frontend uses React + Axios.
- Use English for backlog, endpoint names and database schema.
- Keep business logic simple and aligned with SWP391 scope.

## Run Locally

Backend:

```bash
scripts/run-backend.sh
```

On Windows PowerShell:

```powershell
.\scripts\run-backend.ps1
```

To force the local H2 database even when `.env.local` exists:

```powershell
.\scripts\run-backend.ps1 -Local
```

Frontend:

```bash
scripts/run-frontend.sh
```

Open `http://localhost:5173`.

Default demo accounts all use password `GoalZone@123`:

- `customer@goalzone.local`
- `member@goalzone.local`
- `staff@goalzone.local`
- `admin@goalzone.local`

Registration passwords must be 8-72 characters with uppercase, lowercase, number, special character, and no spaces.

Local dev runs with an in-memory H2 database and seed data. To use Supabase PostgreSQL and SMTP verification email, copy `.env.example` to a local `.env.local`, fill the secrets locally, then run `scripts/check-supabase.sh` and `scripts/run-backend.sh`. The backend script automatically loads `.env` and `.env.local`. Do not commit real database or email passwords.

## Implemented MVP Slice

See `docs/IMPLEMENTED_USE_CASES.md` for backlog-aligned UC coverage, `docs/BACKLOG_RDS_CODE_GAP_ANALYSIS.md` for current RDS/backlog/code mismatches, `docs/IMPLEMENTATION_SCOPE.md` for intentional exclusions, `docs/RDS_RULE_ALIGNMENT.md` for business rule alignment, and `docs/SUPABASE_CONNECTION.md` for Supabase connection notes.
