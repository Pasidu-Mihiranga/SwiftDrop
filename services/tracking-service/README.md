# tracking-service

Real-time tracking: parcel timeline read model (MongoDB), public PII-masked tracking API and Socket.IO live updates.

| | |
|---|---|
| Port | `8090` |
| API base | `/api/v1/tracking` (through the gateway at `http://localhost:8080`) |
| Metrics / health | `/metrics`, `/health/live` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Layout

```
src/
├── main.ts            bootstrap (JSON logging, graceful shutdown)
├── app.module.ts      imports SwiftDropCommonModule (JWT guard, problem+json, metrics) + feature modules
├── config/env.ts      zod-validated environment (fails fast on bad config)
├── modules/<feature>/ controller → service → domain → infrastructure (see modules/README.md)
└── consumers/         Kafka consumers (see consumers/README.md)
```

## Run & test

```bash
pnpm infra:up                                    # repo root: Kafka, Keycloak, Mongo, Redis, ...
pnpm --filter @swiftdrop/tracking-service dev         # watch mode, reads the root .env
pnpm --filter @swiftdrop/tracking-service test        # Jest + coverage gate (≥70% lines)
```

Unit tests run with `pnpm --filter @swiftdrop/tracking-service test`. The Testcontainers integration tests (Kafka,
MongoDB, Redis) need a running Docker daemon.

## Endpoints

| Method and path | Who | Notes |
|---|---|---|
| `GET /api/v1/tracking/public/{trackingNumber}` | anyone | Masked view: city names and masked recipient only. No phone, no street. Rate limited per client IP (`RATE_LIMIT_PUBLIC_PER_MIN`, default 60 a minute), 429 with `Retry-After`. Fails open if Redis is down. |
| `GET /api/v1/tracking/parcels/{parcelId}/timeline` | customer, assigned courier, merchant (`merchant_id` claim), DISPATCHER, ADMIN | Anyone else gets 404, the same as an unknown parcel. |
| `POST /api/v1/tracking/socket-tickets` | signed-in user | Single-use ticket valid 60 seconds, plus the rooms the user may join. |
| `/metrics`, `/health/live` | internal | Prometheus metrics and liveness. |

Contract: [contracts/openapi/tracking-service.v1.yaml](../../contracts/openapi/tracking-service.v1.yaml).

## Real-time

Socket.IO at `/ws/tracking` (through the gateway). Handshake with `auth.ticket` (browsers) or `auth.token` (native
clients), or anonymous for public rooms only. Rooms:

- `tracking:<trackingNumber>`: public masked view, anyone.
- `user:<sub>`: only the token's own subject.
- `merchant:<merchantId>`: only with the matching `merchant_id` claim.
- `dispatch`: DISPATCHER or ADMIN.

Replicas share rooms through the Redis adapter (`REDIS_URL`).

## Events

- Consumes `parcel-created`, `parcel-status-changed` and `parcel-assigned` (contracts in `contracts/events`).
- Idempotent: processed event ids are kept in Mongo (`processed_event`).
- Failed messages go to `swiftdrop.tracking.dead-letter.v1` after 3 attempts.
