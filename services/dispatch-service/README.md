# dispatch-service

Unassigned parcel pool, courier assignment, run sheets and courier workload projection.

| | |
|---|---|
| Port | `8085` |
| Database | PostgreSQL `dispatch_db` (user `dispatch_svc`) |
| API base | `/api/v1/dispatch` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8085/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.dispatch
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/dispatch-service -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/dispatch-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/dispatch-service/target/site/jacoco/index.html`.

## Implemented M5 flow

- `GET /api/v1/dispatch/unassigned?zone=CP&page=0&size=20`: nonterminal,
  unassigned bookings ordered oldest first. Page size is capped at 100.
- `POST /api/v1/dispatch/assignments`: `{ "parcelId": "<uuid>", "courierId": "<JWT-sub>", "runDate": "YYYY-MM-DD" }`.
  `runDate` defaults to today. An existing assignment returns 409; unavailable couriers
  or past dates return 422. Capacity is advisory and does not block allocation.
- `POST /api/v1/dispatch/assignments/{id}/reassign`: `{ "courierId": "<JWT-sub>" }`.
  Retains the assignment ID and date; includes previousCourierId in ParcelAssigned.
- `GET /api/v1/dispatch/assignments?date=YYYY-MM-DD`: assigned runs for the staff board.
- `GET /api/v1/dispatch/workloads`: courier names, zone IDs, capacity, availability,
  activeCount, deliveredToday and failedToday. M1 is queried with the caller token.
- `GET /api/v1/courier/run-sheet?date=YYYY-MM-DD`: calling courier only, using JWT sub.

Staff operations require DISPATCHER or ADMIN; the run sheet requires COURIER. Dates use
`DISPATCH_TIME_ZONE` (default Asia/Colombo). `USER_SERVICE_URL` configures M1; compose sets
its internal hostname. Failed M1 requests fail the operation rather than accepting an
unverified courier. No contact details or street addresses are stored in the projection.

### Projection rules

ParcelCreated fills the unassigned read model. ParcelStatusChanged updates the latest status
only when its changedAt is newer. A placeholder handles status-before-create across topics.
The shared IdempotentConsumer makes processed-event markers and projection writes atomic.
A failed handler retains its Kafka offset and retries; check logs and consumer lag if a poison
record blocks a partition. Resolve malformed records before operationally advancing offsets.

Flyway V2 creates parcel_summary, the unique-per-parcel assignment table and the courier_workload
SQL view. Counts are derived from current projected state, avoiding incremental counter drift.
Active excludes DELIVERED, FAILED and CANCELLED, across all run dates. Delivered/failed today
count current outcomes with a latest status timestamp inside the business day; they are not
an audit count of every historical failed attempt. Capacity is a workload guide, not a hard limit.
The parcel row lock plus fresh read prevents two dispatchers allocating the same parcel.

### UI and demo

Open `/admin/dispatch` as dispatcher or admin. Zone filters, parcel age/size, capacity bars,
zone matching, assignment and reassignment use the existing web BFF and design tokens.
M6 Socket.IO activity triggers data refresh; five-second polling covers projection lag and
socket disconnection. `/courier` displays and refreshes the signed-in courier's daily run sheet.

1. Start the existing stack and sign in with dispatcher, customer, courier1 and courier2 accounts.
2. Configure both courier profiles as active, with a vehicle, home zone and daily capacity.
3. Book a parcel through the customer UI. It appears in the board without a manual refresh.
4. Assign it to courier1: the active count rises and courier1's run sheet includes the parcel.
5. Use Assigned runs to reassign it to courier2: ownership and workloads move together.
6. Replay the exact Kafka envelope (retain eventId): projection and counts remain unchanged.

### Validation and design

Run `mvn -pl services/dispatch-service -am verify` with Docker running and Java 21.
The tests cover Kafka creation/status projection, duplicates, outbox-to-Kafka publication,
reassignment, courier isolation, concurrent allocation and transaction rollback. The inherited
JaCoCo gate enforces at least 70% line coverage. Run `pnpm --filter @swiftdrop/web test` and
`pnpm --filter @swiftdrop/web typecheck` for the UI. `next build --webpack` is a supported
fallback when a restricted environment prevents Turbopack worker ports.

See [the API contract](../../contracts/openapi/dispatch-service.v1.yaml),
[Kafka operations](../../infra/kafka/README.md), and
[the design PDF](../../doc/design/m5-dispatch-workload-design.pdf).
Editable SVG and PlantUML diagrams live in `doc/diagrams/m5-dispatch-workload`.
Regenerate the PDF/SVG with `python scripts/build-m5-design.py` (reportlab required).
The M5 Grafana consumer-lag dashboard reuses M9's Kafka Exporter/Prometheus configuration.
