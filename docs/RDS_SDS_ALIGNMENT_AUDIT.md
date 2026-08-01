# RDS/SDS alignment audit

Audit date: 2026-08-01

## Traceability result

- Backlog, RDS catalogue/descriptions, SDS code designs, class diagrams, and sequence diagrams use UC-01–UC-56.
- RDS has 56 detailed use-case headings. SDS has 56 numbered code-design sections.
- UC-07/08 and UC-16/17 are explicit split flows; removed internal/duplicate rows do not appear as standalone sections.
- Use Case Diagram and Screen Flow sections are deferred pending the team templates and are not filled with provisional artifacts.
- The removed assistant has no canonical UI route, controller/service, API, environment setting, test, RDS screen, SDS section, class diagram, or sequence diagram.

## Code and database result

- Authentication mutations derive the actor from JWT; account lock uses `status`, `lock_reason`, and `auth_version`.
- Bookable slots require a real active price band; there is no USD 12 fallback.
- PayPal capture/refund and promotion capacity are idempotent/auditable.
- Manual booking Confirm/Reject is unavailable; payment confirms and scheduler expiry releases unpaid holds.
- Same-day search and field suggestions omit elapsed starts; preview/create reject stale direct requests.
- Staff may match a counter-safe active-Customer directory or record a first-time visitor's name/phone on the booking, without receiving Customer-management authority or creating an account.
- JPA, DBML, ERD, and Flyway V21 share 19 physical tables; V21 adds nullable guest-contact columns without adding a fake account table.
- `field_type`, `field_price`, and `system_setting` remain normalized, intentional tables.

## Verification evidence

- Backend tests passed 22/22; frontend production build, 56+56 PlantUML audit, and scope-alignment audit passed.
- DOCX is converted to PDF and every page is rendered for layout QA after the final document build.
- The final RDS contains the current classic business-web screenshots; provisional Use Case Diagram and Screen Flow remain deferred for the team templates.
- External presentation checks are limited to SMTP and PayPal Sandbox; local deterministic tests remain the primary evidence.
