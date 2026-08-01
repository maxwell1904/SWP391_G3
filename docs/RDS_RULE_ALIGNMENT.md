# RDS business-rule alignment

Updated: 2026-08-01

- Customer administration exposes View and Lock/Unlock only.
- Locking requires a reason, sets `status = locked`, increments `auth_version`,
  blocks login, and sends email/in-app notification evidence.
- Customer activity detail is available only to Admin.
- Profile update is self-owned for Customer, Staff, and Admin.
- Account deletion and Customer profile editing by Admin are outside scope.
- Reviews/ratings and production PayPal certification are outside scope.
- Admin owns automatic slot opening, closing, duration, and rolling-horizon
  rules; Staff Block/Unblock owns exceptional unavailability. Ordinary
  availability does not depend on manual slot creation or seed data.
- Public search and field suggestions omit same-day slots after their start;
  checkout preview and booking creation reject the same stale selection.
- Staff walk-in booking may select from a counter-safe active-Customer directory or store a first-time visitor name/phone snapshot without creating an account;
  Customer management, activity detail, and lock/unlock remain Admin-only.
- Email covers verification, reset, Staff invitation, account lock/unlock,
  booking, and refund events.
- `AccountController` provides account lock, email verification, self-owned
  profile/password, and password setup/reset endpoints.
- PostgreSQL schema is managed through Flyway V21. V19 reconciles the financial ledger; V20 reconciles promotion usage reservations; V21 adds the registered-or-guest walk-in identity constraint.
