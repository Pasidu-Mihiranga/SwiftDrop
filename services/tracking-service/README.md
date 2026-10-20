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
