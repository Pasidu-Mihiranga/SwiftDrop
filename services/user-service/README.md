# user-service

User profiles, address book, courier profiles and admin user management (Keycloak Admin API adapter).

| | |
|---|---|
| Port | `8081` |
| Database | PostgreSQL `users_db` (user `user_svc`) |
| API base | `/api/v1/users` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.user
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Endpoints

| Method and path | Who | What it does |
|---|---|---|
| `GET /api/v1/users/me` | any person role | Returns the caller's profile. The first call creates it from the token; later calls copy the e-mail and roles from the token again. |
| `PUT /api/v1/users/me` | any person role | Replaces the full name and phone number. Leaving `phone` out removes it. |

A `MERCHANT_API` machine client gets `403`: it has no profile. Full contract:
[contracts/openapi/user-service.v1.yaml](../../contracts/openapi/user-service.v1.yaml).

## Design notes

- **Keycloak owns identity, user-service owns profile data.** `user_profile.id` is the Keycloak subject, so no
  password is stored here and no sign-up event is needed: the row is created just in time on the first
  `GET /users/me` (`INSERT ... ON CONFLICT DO NOTHING`, so simultaneous first requests are safe).
- **Roles are mirrored** into `user_role` from the token, so queries such as "all couriers" do not call Keycloak.
- **Phone numbers are encrypted at rest** with AES-256-GCM (`PiiCipher`, applied by a JPA converter) using
  `PII_ENCRYPTION_KEY`. The service refuses to start without a valid 32-byte key; `pnpm env:init` generates one.
  Numbers are normalised to E.164 (`+947XXXXXXXX`), and `PhoneNumber.toString()` never prints the number.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/user-service -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/user-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/user-service/target/site/jacoco/index.html`.
