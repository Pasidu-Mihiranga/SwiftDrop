# __service__

__description__

| | |
|---|---|
| Port | `__port__` |
| Database | PostgreSQL `__db__` (user `__dbuser__`) |
| API base | `/api/v1/__route__` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:__port__/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.__pkg__
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/__service__ -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/__service__ -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/__service__/target/site/jacoco/index.html`.
