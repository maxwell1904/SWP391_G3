# RDS/SDS Code-First Alignment Audit

Audit baseline: integrated `develop` after AnPTT's UC-51–64 merge plus the GoalZone code-first completion changes.

## Result

- RDS covers UC-01 through UC-64. UC-63 and UC-64 are implemented, not Deferred/Removed.
- SDS contains 64 class diagrams and 64 sequence diagrams.
- Every class and method listed in the SDS Class Specifications is represented in its corresponding class diagram.
- Class diagrams may additionally show DTOs, entities, enums, repositories, and shared helpers needed to understand relationships. Those supporting types do not need duplicate Class Specification tables unless they expose use-case operations.
- The 41 diagrams originally embedded by BonVT are preserved byte-for-byte; only missing diagrams are inserted.
- Membership documentation reflects Flyway V10 and the live lifetime/monthly/consecutive-week qualification logic.
- Refund documentation reflects provider/cash completion and reconciliation of refund, invoice, and booking financial snapshots.

## Confirmed mismatches corrected

| Area | Earlier document/diagram issue | Code-first correction |
|---|---|---|
| UC-17 | Class diagram used the booking-list flow | Uses `FieldOperationController.operationCalendar` and `FieldOperationService.operationCalendar` |
| UC-19 | Class diagram only showed service catalogue listing | Shows overlapping inventory calculation and `BookingServiceItemRepository` aggregation |
| UC-28 | Class Specification mixed operation calendar into booking calendar | Booking calendar now documents only the booking/date flow |
| UC-29, UC-39 | PayPal alternative classes appeared in specs but not diagrams | PayPal/checkout collaborators are represented in the corresponding class diagrams |
| UC-40, UC-43, UC-44, UC-47, UC-59 | Several documented methods were missing from class diagrams | Missing configuration, scheduler, history, provider-refund, and notification/refund methods were added |
| UC-54–56 | Text and diagrams claimed lifetime-only rules or stale public filtering | Period fields, active filtering, Admin `includeInactive`, and period-aware progress are documented |
| UC-63–64 | RDS/SDS said Deferred/Removed | Added implemented descriptions, class/sequence diagrams, database queries, UI handoff, and validation |
| Refunds | Completion could look like a status-only operation | Completed refunds reconcile invoice and booking paid/refundable/remaining snapshots |
| Suggested slot | “Book this slot” did not select the suggested result | Slot id/date are transferred, live slots reloaded, and the exact slot selected only if still available |

## Deliberate scope notes (not document contradictions)

- Field reviews/ratings remain outside the current schema; field detail states this explicitly.
- UC-37 composes calendar, issue, reschedule, cancellation, refund, and notification functions; there is no dedicated conflict entity.
- UC-63 is rule-based and explainable. External AI/LLM/RAG remains an optional enhancement.
- PayPal is Sandbox/classroom scope. Production certification and live-money settlement are outside scope.
- Reports share one service/endpoint; per-UC SQL in SDS is representative equivalent SQL for the relevant aggregate.
- USD 12 is the explicit fallback when a field has no matching configured price band.

## Verification gates

- Backend automated tests: 16 tests.
- Frontend production build.
- PlantUML syntax and structural audit: 64 class + 64 sequence diagrams.
- Class Specification component/method-to-diagram audit: UC-01 through UC-64.
- DOCX render inspection, including the final UC-63/64 pages and preserved existing diagrams.
