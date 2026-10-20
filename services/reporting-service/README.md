# reporting-service

Event-driven reporting projections: deliveries per day, success/failure rates, courier performance.

| | |
|---|---|
| Port | `8086` |
| Database | PostgreSQL `reporting_db` (user `reporting_svc`) |
| API base | `/api/v1/reports` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8086/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.reporting
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/reporting-service -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/reporting-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/reporting-service/target/site/jacoco/index.html`.
