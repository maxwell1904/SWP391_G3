# RDS business-rule alignment

Updated: 2026-07-26

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
- Email covers verification, reset, Staff invitation, account lock/unlock,
  booking, and refund events.
- `AccountController` provides account lock, email verification, self-owned
  profile/password, and password setup/reset endpoints.
- PostgreSQL schema is managed through Flyway V19. V19 reconciles gross collected payments, invoice refunds, and financial relationship constraints.
