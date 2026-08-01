# Backlog–RDS–code gap analysis

Audit date: 2026-08-01

## Result

- The final backlog, RDS, SDS, class diagrams, sequence diagrams, frontend, backend, and Flyway V21 schema use UC-01–UC-56.
- The two justified splits are Customer View vs Lock/Unlock and Admin slot rules vs Staff block exceptions.
- Nine submitted rows were removed/demoted because they were internal steps or duplicate journeys; their retained behavior is documented inside the parent use cases.
- The assistant/suggestion UI, route, API, services, environment configuration, and tests were removed together.
- Manual Staff booking Confirm/Reject actions were removed from UI and rejected by the status endpoint; payment/scheduler behavior owns confirmation/expiry.
- The 19 JPA table mappings, DBML, ERD, and Flyway migrations remain aligned. No schema table existed solely for the removed assistant.
- Use Case Diagram and Screen Flow are intentionally deferred until the team supplies its draw.io and eraser.io templates.

## Presentation checks

- Demonstrate registration/verification or password recovery using the configured SMTP mailbox.
- Demonstrate one PayPal Sandbox buyer approval and, if time allows, one approved provider refund.
- Demonstrate an unpaid hold expiring and a reminder being de-duplicated through automated evidence or seeded data.
- Explain that JPA entity/repository/service code is where Hibernate behavior changes; Flyway migrations are where PostgreSQL schema changes are versioned.
- Keep `.env.local` and all credentials outside submitted Git history.
