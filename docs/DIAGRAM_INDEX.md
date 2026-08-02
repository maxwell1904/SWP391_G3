# GoalZone diagram index

Updated: 2026-08-01

The canonical design set contains 56 class diagrams and 56 sequence diagrams, one pair for each UC-01–UC-56. `python3 docs/audit_puml_diagrams.py` verifies the count, PlantUML structure, and sequence activation balance.

| Range | Area | Count |
| --- | --- | ---: |
| UC-01–UC-10 | Account and access | 10 pairs |
| UC-11–UC-23 | Fields, slots, services, and issues | 13 pairs |
| UC-24–UC-33 | Booking and venue operations | 10 pairs |
| UC-34–UC-44 | Checkout, payments, refunds, and policy | 11 pairs |
| UC-45–UC-56 | Promotions, membership, notifications, and reports | 12 pairs |

## Split designs

| UC | Class source | Sequence source |
| --- | --- | --- |
| UC-07 View customer accounts | `Class Diagram/manage-customer-accounts.puml` | `Sequence/UC-07-view-customer-accounts.puml` |
| UC-08 Lock/unlock customer account | `Class Diagram/lock-unlock-customer-account.puml` | `Sequence/UC-08-lock-unlock-customer-account.puml` |
| UC-16 Configure slot generation rules | `Class Diagram/configure-slot-generation-rules.puml` | `Sequence/UC-16-configure-slot-generation-rules.puml` |
| UC-17 Block/unblock field slots | `Class Diagram/block-unblock-field-slots.puml` | `Sequence/UC-17-block-unblock-field-slots.puml` |

All other source filenames use the final UC number in `docs/Sequence/` and a matching behavior name in `docs/Class Diagram/`.

## External editable diagrams

- Use Case Diagram: `Use Case Diagram/GoalZone_Use_Case_Diagrams.drawio`.
- Screen Flow: four actor-based sources under `Screen Flow/`: Guest, Customer, Venue Staff, and Admin.

These sources reflect the current 56-use-case scope and implemented UI journeys. They remain external editable artifacts so the team can finish diagram placement before embedding exported images in RDS/SDS. Earlier provisional sources under `docs/archive/` are not canonical.

Retired standalone diagrams for included/internal or removed items are preserved under `docs/archive/retired-uc-diagrams-2026-08-01/` for audit history only.
