# parcel-service

Parcel lifecycle: booking, tracking numbers, status state machine, proof of delivery, merchant order API, receipts.

| | |
|---|---|
| Port | `8084` |
| Database | PostgreSQL `parcels_db` (user `parcel_svc`) |
| API base | `/api/v1/parcels` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8084/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.parcel
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/parcel-service -am spring-boot:run   # reads secrets from the root .env
```

## Merchant order API (M7)

`POST /api/v1/merchant/orders` (role `MERCHANT_API`, header `Idempotency-Key`) and
`GET /api/v1/merchant/orders` (roles `MERCHANT` or `MERCHANT_API`). The merchant is always the one
in the access token's `merchant_id` claim. The contract is `contracts/openapi/parcel-service.v1.yaml`.

Only a merchant that parcel-service knows as `APPROVED` may order. parcel-service learns this from
merchant-service's `MerchantLifecycleChanged` events on `swiftdrop.merchant.lifecycle.v1`, kept in
the `merchant_status` table (`MerchantLifecycleListener`). A merchant it has never heard of is
refused with 403 (fail closed).

- **Duplicates and late events.** Each event is recorded in `processed_event` (consumer
  `parcel-merchant-status`) in the same transaction as the status update, so a redelivery changes
  nothing. An event older than the stored status (by `changedAt`) is ignored, so a late APPROVED can
  never re-open a SUSPENDED merchant.
- **Failures.** A failed update rolls back, is not marked as consumed, and is retried with
  exponential backoff (`swiftdrop.kafka.retry.initial-interval`, default 1s, doubling up to
  `max-interval`, default 30s) without giving up, so the partition waits rather than losing a
  status change. Each retry is logged at WARN with the record's position and the failure's type;
  record values are never logged. A malformed message is logged at ERROR and skipped. There is no
  dead-letter topic for this consumer yet.

### Known limitation: seven-day topic retention

`swiftdrop.merchant.lifecycle.v1` keeps messages for seven days (`retention.ms=604800000` in
`infra/kafka/create-topics.sh`) and is not compacted. parcel-service only knows merchants whose
events it read, so:

- a **new** deployment, or a new consumer group, sees only the last seven days of lifecycle changes;
- any merchant approved earlier is unknown to it and gets 403 on every order, although
  merchant-service shows it as approved.

This does not affect environments where parcel-service has been running since merchants were first
approved. A future fix, outside parcel-service, is one of:

1. **Compaction:** make the topic `cleanup.policy=compact` (keyed by merchant id, which it already
   is), so the latest event per merchant is kept indefinitely; or
2. **Resync:** have merchant-service republish the current status of every merchant (for example an
   admin-triggered job writing one `MerchantLifecycleChanged` per merchant through its outbox).
   parcel-service needs no change for either: old and duplicate events are already ignored.

Until then, the only stopgap is operational: copy an affected merchant's current status from
`merchants_db.merchant` into `parcels_db.merchant_status` by hand, with `changed_at` set to the
merchant's `reviewed_at`, so any later real event still supersedes it:

```sql
-- parcels_db; values taken from merchants_db.merchant for that merchant
INSERT INTO merchant_status (merchant_id, status, business_name, contact_email, changed_at, updated_at)
VALUES ('<merchant id>', 'APPROVED', '<business_name>', '<contact_email>', '<reviewed_at>', now())
ON CONFLICT (merchant_id) DO NOTHING;
```

Do not suspend and re-approve a merchant to force an event: SUSPENDED is final in merchant-service.

## Test

```bash
mvn -pl services/parcel-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/parcel-service/target/site/jacoco/index.html`.

## Courier status and delivery proof (M4)

Assigned couriers can advance a parcel through the documented lifecycle at
`PATCH /api/v1/courier/parcels/{parcelId}/status`. The service validates transitions,
failure reasons and delivery proof, then saves the parcel, append-only history and
`ParcelStatusChanged` outbox event in one transaction. Dispatch assignment events update
the copied `courier_id` idempotently.

For photo proof, request a presigned URL from
`POST /api/v1/courier/parcels/{parcelId}/proof-upload-url` with a JPEG or PNG content type
and size between 1 byte and 5 MB, upload directly to RustFS, then include the returned
`proofId` when marking the parcel `DELIVERED`. `S3_ENDPOINT` is the service-to-storage
endpoint; `S3_PUBLIC_ENDPOINT` is the browser-visible endpoint embedded in presigned URLs.
