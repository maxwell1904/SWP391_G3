# GoalZone coherent retake scope

Status: canonical scope decision for the retake refactor. The number of use cases is the result of tracing actor goals through the implemented product, not a quota.

## Outcome

- Baseline: 63 submitted items.
- Remove or demote 9 items that are duplicate journeys or internal steps.
- Split 2 genuinely overloaded items along actor and postcondition boundaries.
- Final catalogue: **56 use cases**.
- Allocation: BonVT 10, BaoNG 13, NgocPA 10, AnNP 11, AnPTT 12. Every member retains at least 10 coherent use cases; Contribution remains 20% per member.

The scope keeps all useful core behavior: operation calendar, editable booking add-ons, payment timeout recovery, reminders, detailed reports, PayPal Sandbox, promotion, membership, refunds, and policy configuration.

## Classification rules

A backlog row remains a use case when it has a recognizable external actor goal, trigger, authorization boundary, main flow, and observable business postcondition. A step is demoted to an included flow or alternate flow when it exists only to support another goal. CRUD verbs are not split merely to increase the count.

### Two justified splits

1. Old UC-07 combines read-only customer inspection with a security mutation:
   - **View customer accounts** lists and opens business-facing Customer information.
   - **Lock/unlock customer account** requires a reason, changes sign-in state, increments `authVersion`, revokes old JWTs, and notifies the Customer.

2. Old UC-15 combines different actors, permissions, and postconditions:
   - **Configure slot generation rules** lets Admin maintain opening time, closing time, slot duration, and rolling horizon.
   - **Block/unblock field slots** lets Staff record operational exceptions and prevents conflicts with active bookings.

`Forgot/reset password` is not split. It is renamed **Recover password** because requesting a one-time link and setting a new password are two phases of one Guest goal.

### Nine removals or demotions

| Old ID | Submitted item | Final treatment |
| --- | --- | --- |
| UC-18 | Check extra-service availability | Included validation inside Add/Update booking services; no separate user journey |
| UC-28 | Confirm booking | Successful required payment confirms an online booking automatically; walk-in creation records its valid initial state |
| UC-29 | Reject booking request | Removed manual approval queue; invalid or unavailable requests fail transactionally in Create/Pay booking |
| UC-31 | Preview cancellation fee/refund | Mandatory first step inside Cancel booking |
| UC-36 | Handle booking conflict | Alternate flow that invokes reschedule, cancellation, issue, refund, and notification behavior |
| UC-40 | Capture/confirm online payment | PayPal provider step inside Pay online; still persisted and idempotent |
| UC-44 | Generate booking invoice | System postcondition recalculated by booking/payment changes; users access it through View invoice/payment status |
| UC-62 | Ask availability assistant | Feature and Gemini dependency removed; ordinary availability search is sufficient |
| UC-63 | Get ranked available slot suggestions | Duplicate assistant journey removed; UC-13 search remains authoritative and revalidates at checkout |

Automated payment expiry and notifications remain because they each have a distinct external trigger and observable business outcome. They will use `Scheduler`, `PayPal`, or the receiving Customer as external actors in the final diagram rather than treating GoalZone as its own actor.

## Final catalogue

### BonVT — Account and access (10)

| New ID | Source | Use case |
| --- | --- | --- |
| UC-01 | old UC-01 | Register account |
| UC-02 | old UC-02 | Login |
| UC-03 | old UC-03 | Logout |
| UC-04 | old UC-04 | Manage personal profile |
| UC-05 | old UC-05 | Change password |
| UC-06 | old UC-06 | Recover password |
| UC-07 | split old UC-07 | View customer accounts |
| UC-08 | split old UC-07 | Lock/unlock customer account |
| UC-09 | old UC-08 | Manage staff accounts |
| UC-10 | old UC-09 | View customer activity |

### BaoNG — Fields, slots, services, and issues (13)

| New ID | Source | Use case |
| --- | --- | --- |
| UC-11 | old UC-10 | View field list |
| UC-12 | old UC-11 | View field detail |
| UC-13 | old UC-12 | Search available fields by date/time |
| UC-14 | old UC-13 | Manage football fields |
| UC-15 | old UC-14 | Manage field pricing by time range |
| UC-16 | split old UC-15 | Configure slot generation rules |
| UC-17 | split old UC-15 | Block/unblock field slots |
| UC-18 | old UC-16 | View field operation calendar |
| UC-19 | old UC-17 | Manage extra services |
| UC-20 | old UC-19 | Add extra services to booking |
| UC-21 | old UC-20 | Update extra services before check-in |
| UC-22 | old UC-21 | Report field/service issue |
| UC-23 | old UC-22 | Resolve field/service issue |

### NgocPA — Booking and venue operations (10)

| New ID | Source | Use case |
| --- | --- | --- |
| UC-24 | old UC-23 | Create online booking |
| UC-25 | old UC-24 | Create walk-in booking |
| UC-26 | old UC-25 | View booking detail |
| UC-27 | old UC-26 | View my bookings |
| UC-28 | old UC-27 | View booking calendar |
| UC-29 | old UC-30 | Reschedule booking |
| UC-30 | old UC-32 | Cancel booking |
| UC-31 | old UC-33 | Check in booking |
| UC-32 | old UC-34 | Complete booking |
| UC-33 | old UC-35 | Mark no-show |

### AnNP — Checkout, payments, refunds, and policy (11)

| New ID | Source | Use case |
| --- | --- | --- |
| UC-34 | old UC-37 | View checkout summary |
| UC-35 | old UC-38 | Choose payment option |
| UC-36 | old UC-39 | Pay online via PayPal Sandbox |
| UC-37 | old UC-41 | Confirm remaining payment |
| UC-38 | old UC-42 | Handle failed/expired payment |
| UC-39 | old UC-43 | View payment history |
| UC-40 | old UC-45 | View invoice/payment status |
| UC-41 | old UC-46 | Process online refund |
| UC-42 | old UC-47 | Manage refund requests |
| UC-43 | old UC-48 | Configure deposit rules |
| UC-44 | old UC-49 | Configure cancellation/refund policy |

### AnPTT — Promotions, membership, notifications, and reports (12)

| New ID | Source | Use case |
| --- | --- | --- |
| UC-45 | old UC-50 | View active promotions |
| UC-46 | old UC-51 | Manage promotion campaigns |
| UC-47 | old UC-52 | Apply promotion to booking |
| UC-48 | old UC-53 | View membership progress |
| UC-49 | old UC-54 | Manage membership rules |
| UC-50 | old UC-55 | View membership benefits |
| UC-51 | old UC-56 | Send booking confirmation notification |
| UC-52 | old UC-57 | Send booking reminder notification |
| UC-53 | old UC-58 | Send cancellation/refund notification |
| UC-54 | old UC-59 | View revenue report |
| UC-55 | old UC-60 | View booking report |
| UC-56 | old UC-61 | View customer activity report |

## Boundaries that must stay consistent

- `Manage staff accounts`, `Manage football fields`, `Manage extra services`, `Manage promotion campaigns`, and `Manage membership rules` contain normal create/read/update/activate/deactivate flows.
- `Recover password` contains request-link, token validation, and reset phases.
- `Cancel booking` contains preview, confirmation, fee/refund calculation, slot release, and notification effects.
- `Pay online` contains order creation, provider approval, capture, idempotent retry, and provider failure behavior.
- `Handle failed/expired payment` covers the externally visible failure state and scheduled release of an aged unpaid hold, not PayPal capture internals.
- `View invoice/payment status` consumes the invoice that the system maintains as a postcondition.
- `Manage refund requests` and `Process online refund` remain separate because review-state handling and provider execution have different actors and failure modes.
- Raw database identifiers are not presented as useful business information. UI uses booking code, field, customer, issue description/status, and dates; IDs remain transport keys only.

## Diagram policy

- Provisional use-case diagrams and screen flows are excluded from RDS/SDS.
- Final use-case diagrams will use the supplied draw.io template.
- Final screen flows will use the supplied eraser.io template.
- Numbering and navigation are frozen before those two artifacts are generated.

## Migration order

1. Enforce the final booking/payment behavior in code and remove assistant/manual approval UI/API paths.
2. Build a 56-row backlog directly from this catalogue and preserve 20% contribution per member.
3. Renumber retained class/sequence traceability once using the source-to-final map above.
4. Rewrite RDS/SDS catalogues and descriptions; internal steps appear only within their parent use cases.
5. Verify database constraints and keep historical statuses/migrations compatible even when old UI actions are no longer reachable.
6. Run backend tests, frontend build, browser smoke tests, diagram audits, DOCX/PDF rendering, and checksum generation.
