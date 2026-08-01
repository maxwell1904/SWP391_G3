# AnPTT defense and QA guide — UC-45 to UC-56

## Ownership

| UC | What to demonstrate | Core rule to explain |
| --- | --- | --- |
| UC-45 View active promotions | Guest/Customer promotion list | Only active campaigns inside their period are public |
| UC-46 Manage promotion campaigns | Admin create/update/status/banner | Mutations are Admin-only |
| UC-47 Apply promotion to booking | Checkout preview/create with code | One code; eligibility is server-side; discount is capped and snapshotted |
| UC-48 View membership progress | Customer Account progress | Active rule can be lifetime, current month, or consecutive weeks |
| UC-49 Manage membership rules | Admin rule/status/benefit edit | Ordering, thresholds, period, streak, and discount are validated |
| UC-50 View membership benefits | Public membership list | Only active levels/benefits are shown |
| UC-51 Confirmation notification | Complete valid payment/registered-Customer booking confirmation | One visible event for the affected Customer; a guest walk-in has no account notification |
| UC-52 Booking reminder | Scheduler evidence | Configured window and duplicate guard |
| UC-53 Cancellation/refund notification | Cancel or complete refund | Notification follows the persisted business event |
| UC-54 Revenue report | Admin period filter | Persisted gross, provider fee/net, completed refund, and net revenue |
| UC-55 Booking report | Admin period filter | Only qualifying statuses count utilization/peak demand |
| UC-56 Customer activity report | Admin period filter | Aggregate period analytics, distinct from UC-10 one-Customer drill-down |

## High-risk questions

**Why does a pending booking reserve promotion usage?**  It prevents concurrent holds from exceeding campaign capacity. Confirmation keeps the reservation; cancellation or expiry releases it. Flyway V20 adds `booking_promotion.usage_counted` so retry/reconciliation is idempotent.

**Why are there two Customer activity UCs?**  UC-10 opens one Customer for operational support. UC-56 aggregates returning/top-customer and membership statistics across a selected period. Different trigger, output, and decision.

**Are notifications separate UCs?**  They remain because each has a distinct externally visible result; UC-52 also has an independent Scheduler trigger and duplicate guard. They are not database writes disguised as UCs.

**Where did UC-62/63 go?**  The assistant/ranked-suggestion journey was removed. UC-13 performs ordinary deterministic availability search, keeping the product easier to defend and closer to the venue workflow.

## Quick QA

1. Apply a promotion to a pending booking; confirm `usedCount` increases once.
2. Cancel or expire it; confirm capacity is released once.
3. Complete a qualifying booking; confirm membership progress changes and no-show does not count.
4. Run the reminder job twice; confirm only one reminder exists for the booking.
5. Compare report results with cancelled/expired rows and with dates outside the selected period.
