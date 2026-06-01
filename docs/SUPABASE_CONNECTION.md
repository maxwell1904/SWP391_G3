# Supabase Connection Notes

Project: `SWP_391`
Ref: `fzlrxfcdlbaiildwjkxo`
Region: `ap-southeast-1`
Database host: `db.fzlrxfcdlbaiildwjkxo.supabase.co`

The Supabase connector and local `psql` can query the database successfully. The Spring Boot app has generated the MVP schema in `public` through JPA and seeded demo data. Local `psql`/JDBC direct connection may fail on networks without IPv6 routing because the direct database host resolves to IPv6 only.

Use one of these for local Spring Boot:

- Supavisor pooler string from Supabase Dashboard:
  `jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres`
  with username `postgres.fzlrxfcdlbaiildwjkxo`.
- Direct DB string if IPv6 is available:
  `jdbc:postgresql://db.fzlrxfcdlbaiildwjkxo.supabase.co:5432/postgres`

Put the final values in `.env.local`; do not commit real passwords.
