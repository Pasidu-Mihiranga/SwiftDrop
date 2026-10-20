# notification-service

Consumes parcel/merchant events and sends email (SMTP/Mailpit) and mock SMS notifications through pluggable channels.

| | |
|---|---|
| Port | `8091` |
| API base | `/api/v1/notifications` (through the gateway at `http://localhost:8080`) |
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
pnpm --filter @swiftdrop/notification-service dev         # watch mode, reads the root .env
pnpm --filter @swiftdrop/notification-service test        # Jest + coverage gate (≥70% lines)
```
