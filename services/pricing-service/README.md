# pricing-service

Delivery zones, rate cards and fee quotes (Strategy + Decorator pricing pipeline).

| | |
|---|---|
| Port | `8083` |
| Database | PostgreSQL `pricing_db` (user `pricing_svc`) |
| API base | `/api/v1/pricing` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8083/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.pricing
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/pricing-service -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/pricing-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/pricing-service/target/site/jacoco/index.html`.
