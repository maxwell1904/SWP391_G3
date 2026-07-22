# Supabase / PostgreSQL setup

The normal runtime uses Supabase PostgreSQL and is migration-first: Flyway creates and hardens the schema, then Hibernate runs with `ddl-auto=validate`. This keeps the code model and database from silently drifting.

## Local H2

Use `scripts/run-backend.sh`. When `.env.local` contains a PostgreSQL URL, the launcher automatically activates the PostgreSQL/Flyway profile. Without Supabase variables it uses a file-backed H2 development database under `backend/data`, so records survive restarts. Automated tests supply their own disposable in-memory database.

## Supabase

Copy `.env.example` to `.env.local`, enter the Supavisor connection values and a long random `JWT_SECRET`, then set:

```dotenv
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://<pooler-host>:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=<database-password>
```

Use the Supavisor pooler string shown in the Supabase Dashboard for local development. The direct `db.<project-ref>.supabase.co` host can require IPv6 routing. Never commit the password, Supabase service-role key, or JWT secret.

For a new Supabase project, start the backend once. Flyway applies:

1. `V1__create_core_schema.sql` — tables, `bigint` identity keys, foreign keys, and entity-aligned PayPal/password-reset columns.
2. `V2__add_integrity_constraints_and_indexes.sql` — enum/value checks, non-overlapping slots, one active booking per slot, one current membership per customer, foreign-key indexes, and refund integrity trigger.
3. `V3__index_slot_created_by.sql` — completes the supporting index for staff calendar/audit joins through `slot.created_by`.
4. `V4__convert_legacy_vnd_amounts_to_usd.sql` — converts previously seeded VND amounts at the documented 25,000 VND/USD classroom rate and makes USD the single display/PayPal settlement currency.

For the existing classroom Supabase schema that Hibernate already created, take a backup and run the read-only preflight before the first Flyway startup:

```bash
psql "$DATABASE_URL" --set ON_ERROR_STOP=1 --file database/supabase/preflight.sql
scripts/run-backend.sh
scripts/verify-supabase-schema.sh
```

`baseline-on-migrate` stamps a non-empty old schema at V1, then applies later migrations. A clean schema runs every versioned migration normally. Preflight must be clean: resolve duplicates rather than deleting data blindly. The app will fail fast at startup if Flyway or Hibernate validation finds a schema mismatch.

## Supabase security boundary

The React frontend must call the Spring API only. It must not receive a Supabase database password or service-role key. The backend uses the database connection; Supabase RLS is therefore not a substitute for Spring Security authorization. If Supabase REST/Storage is later exposed directly to clients, add RLS policies in a separate reviewed migration.

## Migration caveats

- `customer_membership` represents one mutable current row per customer because the current repository returns one `Optional`. Add a `membership_history` model before allowing membership history.
- Slot overlap is blocked at the database level. Field-price overlap is still a business-rule concern because `day_type=all` can overlap weekday/weekend rules; keep that validation in the pricing service until the rule model is normalized.
- The refund trigger limits approved, processing, and completed refunds against `booking.paid_amount`. It does not issue a payment-gateway refund; that remains workflow integration code.
