# reporting-service

Event-driven reporting projections: deliveries per day, success/failure rates, courier performance.

| | |
|---|---|
| Port | `8086` |
| Database | PostgreSQL `reporting_db` (user `reporting_svc`) |
| API base | `/api/v1/reports` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8086/swagger-ui.html` |
| Contract | [contracts/openapi/reporting-service.v1.yaml](../../contracts/openapi/reporting-service.v1.yaml) |
| Owner | M10, see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Endpoints (DISPATCHER, ADMIN)

| Endpoint | Returns |
|---|---|
| `GET /reports/daily?from&to[&zone]` | One entry per day (zero-filled) plus period totals |
| `GET /reports/success-rate?from&to[&zone]` | Delivered vs failed attempts and the rate |
| `GET /reports/zones?from&to` | Totals per dropoff zone |
| `GET /reports/couriers?from&to` | Delivered / failed per courier, most deliveries first |

`from`/`to` are inclusive business days in `REPORTING_TIME_ZONE` (default `Asia/Colombo`), at most 366 days
apart. `successRate` = delivered / (delivered + failed), or `null` when nothing was attempted. Other roles
get 403; a reversed or too-long period gets 400 `INVALID_REPORT_PERIOD`.

## How the projection works

The service consumes `parcel.created`, `parcel.status-changed` and `dispatch.parcel-assigned` (consumer group
`reporting-service`) and owns no business data.

```
event ─► processed_event (drop duplicates)
      ─► parcel_fact / status_transition   (facts, upserted)
      ─► daily_delivery_stats / courier_daily_stats  (rebuilt for every day the parcel touches)
```

- **Order-independent.** Kafka only orders events within a partition of one topic, so a status change can
  arrive before its `ParcelCreated`. Rollups are therefore *recomputed* from the facts rather than
  incremented. Activity seen before `ParcelCreated` sits under zone `UNKNOWN` and moves to the real
  zone when that event arrives.
- **Counting.** `created` counts parcels created on the day; `delivered`/`failed`/`cancelled` count
  transitions on the day, so a parcel that fails once and is then delivered adds one to each. Zone =
  dropoff zone. A transition with no courier id is credited to the parcel's latest assignment.
- **Concurrency.** A transaction-scoped advisory lock per parcel serialises events of one parcel; a lock
  per day (taken in date order) serialises rebuilds of the same day.
- **Bad records.** Malformed JSON, a wrong event type or an unknown `eventVersion` is skipped at once;
  other failures are retried with backoff (about 35 s) and then skipped. Skips are logged with the
  record position and event id (never the payload) and counted in `reporting_events_rejected_total`.
- **Rebuilding.** The tables are derived data. To rebuild, stop the service, truncate `parcel_fact`,
  `status_transition`, both stats tables and the `reporting-*` rows of `processed_event`, reset the
  consumer group to the earliest offset, and start it again.

## Metrics (`/actuator/prometheus`)

| Metric | Meaning |
|---|---|
| `reporting_events_processed_total{type,outcome}` | Events handled; `outcome` is `applied` or `duplicate` |
| `reporting_events_rejected_total{reason}` | Skipped records; `reason` is `malformed` or `retries_exhausted` |
| `reporting_projection_lag_seconds` | Timer from an event occurring to it being reflected in the reports |

Consumer lag for group `reporting-service` comes from kafka-exporter.

## Run locally

```bash
pnpm infra:up                                            # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/reporting-service -am spring-boot:run   # reads secrets from the root .env
```

### Demo data

Until parcel-service and dispatch-service publish real events, seed 30 days of realistic history:

```bash
node services/reporting-service/scripts/seed-demo-events.mjs              # --days 30 --per-day 25 --seed 7
node services/reporting-service/scripts/seed-demo-events.mjs --dry-run    # print the events instead
```

Events follow the JSON Schemas in `contracts/events`, use the pricing-service zone codes, and contain no
real personal data. Re-running with the same seed on the same day re-sends identical events, which are
ignored as duplicates.

## Test

```bash
mvn -pl services/reporting-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

`ReportingProjectionIT` feeds the shared contract examples through real Kafka and Postgres and covers
out-of-order delivery, duplicates, a poison record, and a failed-then-delivered parcel.
`ReportControllerTest` covers role checks (CUSTOMER, COURIER, MERCHANT get 403) and input validation.

Coverage report: `services/reporting-service/target/site/jacoco/index.html`.
