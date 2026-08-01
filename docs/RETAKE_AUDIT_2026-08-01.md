# GoalZone retake audit

Audit date: 2026-08-01

## Verdict

The booking/payment core was viable, but the submitted 63-row scope mixed actor goals with validations, provider callbacks, duplicated journeys, and an assistant feature that made the product harder to explain. The retake baseline uses 56 coherent use cases without trimming useful operations merely to reach a round number.

## Material corrections

| Area | Submitted problem | Retake baseline |
| --- | --- | --- |
| Traceability | Backlog/RDS/SDS/diagram numbers and names drifted | One canonical UC-01–UC-56 map; 56 class + 56 sequence diagrams |
| Feature vs UC | Capability labels duplicated use-case names | Feature is a stable capability group; UC is an external actor goal |
| Overloaded items | Customer View+Lock and Admin rules+Staff blocks mixed actors/postconditions | Split into UC-07/08 and UC-16/17 |
| Internal steps | Validation, preview, capture, invoice generation claimed as standalone goals | Included in add-on/cancel/pay/view-billing parent flows |
| Booking approval | Self-service booking also had manual Staff Confirm/Reject | Valid payment confirms; invalid create/pay fails; scheduler expires unpaid holds |
| Identity/audit | Browser could submit mutation actor IDs | JWT principal is the only mutation actor |
| Pricing | Missing price silently became USD 12 | Unpriced slots are not bookable |
| Operations | Admin could reach Staff branches; timing/payment guards were weak | Role, start time, deposit, remaining balance, and no-show windows are enforced |
| UI | Persistent success toast, raw issue ID, AI-like gradients/cards, overly combined workspaces | Auto-dismiss, business context, conventional business styling, and task-specific tabs/panels |
| Walk-in booking | The Staff flow incorrectly required an existing account and therefore rejected a realistic first-time visitor | Staff can either match a counter-safe Customer row by phone/email or store guest name/phone on the booking; no fake account or Customer-admin authority is introduced |
| Same-day availability | Previously generated rows could remain visible after their start time | Search and field suggestions omit elapsed starts; preview/create still revalidate server-side |
| Assistant | Gemini/suggestion journey duplicated ordinary search and expanded defense surface | UI/API/service/config/test removed; UC-13 remains authoritative |

## Scope that deliberately remains

- PayPal Sandbox, SMTP, operation calendar, add-on editing, payment timeout, booking reminders, detailed reports, promotions, membership, refund provider flow, and Admin policy configuration.
- USD remains consistent across field prices, invoices, and PayPal Sandbox. A VND conversion would be a separate money migration.
- Historical database statuses and immutable migrations remain compatible even when current UI actions are removed.

## Defense focus

Demonstrate one coherent path: browse/search → price → book → PayPal/cash → venue operation → cancel/refund → report. For each action, explain the React page, controller endpoint, service rule, repository/entity, and affected table. Use the 56-row backlog as the index, not as 56 unrelated demos.
