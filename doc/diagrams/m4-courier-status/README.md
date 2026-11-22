# Courier Operations and Status Lifecycle

Author: Pamindu Piyumaka. Design documentation for issue #17.

The PDF follows the six-topic structure of the supplied Webhooks and External Integration example: Use Cases, Data Model, Architecture, Integration Design, Key Flows, and Design Patterns and Principles. It includes Deployment and Scaling, a numbered integration table, and separate lists of patterns and principles.

## Deliverables

- `../../../output/pdf/SwiftDrop_Courier_Operations_Design.pdf`: complete 17-page design document.
- `../../m4-design.pdf`: convenient repository documentation copy of the same PDF.
- `01-courier-use-cases.svg`: courier use cases and conditional proof/failure forms.
- `02-parcel-state.svg`: all allowed transitions and guards.
- `03-status-update-sequence.svg`: authorization, validation, atomic persistence and outbox relay.
- `04-event-consumers-sequence.svg`: independent tracking, notification, webhook and reporting reactions.
- `05-proof-upload-sequence.svg`: direct private S3 upload, validation and delivery finalization.
- `06-module-architecture.svg`: courier operations layers and booking/dispatch boundaries.
- `07-integration-design.svg`: assignment consumption and status-event fan-out.
- `08-state-pattern.svg`: State pattern and infrastructure ports.
- `09-courier-er.svg`: parcel, status history and proof entities with keys and cardinalities.
- `10-event-persistence-er.svg`: outbox/deduplication tables and logical references to identity and dispatch.
- `11-courier-workflow-activity.svg`: decisions and merged branches for normal progress, delivery proof, failure details and rejected commands.
- `12-photo-proof-activity.svg`: upload authorization, direct PUT, error handling and retry.
- `13-numbered-integration.svg`: numbered synchronous and asynchronous integration patterns matching the report table.
- `14-deployment-scaling.svg`: runtime services, infrastructure and deployment boundaries.

Matching PNG exports are included. SVGs remain editable vector graphics. The Python source is the authoritative drawing source.

## Rebuild

Requires Python and ReportLab. From the repository root:

```powershell
python doc/diagrams/m4-courier-status/src/build_structured_report.py
```

The script builds the structured PDF from existing vector sources and generates the numbered integration and deployment SVGs. The older `build_pdf.py` maintains the original diagram sources and earlier report layout. The final report uses 13 diagrams; the older integration overview remains available as a separate source. `src/structured_export_pages.json` records the figure pages and crop coordinates. PNGs are rendered crops, not separate design sources. Refresh `doc/m4-design.pdf` after rebuilding.

## Report guidance checked

- `doc/01-architecture.md`, sections 3 and 5, requires ER coverage and describes the parcel data model. `doc/00-master-plan.md` contains the earlier version of this requirement.
- `doc/03-team-contribution.md` lists the courier behavioral diagrams. It does not replace the report-wide ER requirement.
- The full-system ER already lives in `doc/diagrams/system/`. This report adds a focused relational model for courier operations and a separate view of event persistence and cross-service IDs.
- Parcel domain tables are proposed: `services/parcel-service/src/main/resources/db/migration/V1__baseline.sql` currently contains only the baseline.
- Existing `outbox_event` and `processed_event` columns and keys were checked against shared-library migrations `libs/java/swiftdrop-outbox/src/main/resources/db/migration/V0_1__swiftdrop_outbox.sql` and `V0_2__swiftdrop_processed_event.sql`.

## Design boundaries

- Dispatch & Workload owns allocations and the courier run-sheet API.
- Customer Booking shares the parcel aggregate and owns booking/cancellation orchestration.
- The event contracts in `contracts/events` govern statuses, failure enums and payload structure.
- Proof finalization checks, class names and command response fields are design proposals. Exact re-attempt/proof-view routes and expiry durations remain implementation decisions.
- Test guidance and coverage are targets, not claims of completed checks.
