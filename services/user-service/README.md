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
| `GET/POST /api/v1/users/me/addresses`, `PUT/DELETE .../{addressId}` | any person role | The caller's address book: at most 10 addresses, exactly one default while any exist. Another person's address answers 404. |
| `GET /api/v1/admin/couriers?zoneId=&assignable=` | DISPATCHER, ADMIN | Courier directory for dispatch: every Keycloak user with role COURIER plus vehicle, home zone and daily capacity. |
| `PUT /api/v1/admin/couriers/{courierId}` | ADMIN | Sets a courier's vehicle, home zone, daily capacity and availability. |
| `GET /api/v1/admin/users?search=&role=&enabled=&page=&size=` | ADMIN | Every Keycloak account (service accounts left out) with roles and status, filtered by name or e-mail, role and status, sorted by name, at most 100 a page. |
| `POST /api/v1/admin/users` | ADMIN | Creates a COURIER, DISPATCHER or ADMIN account and e-mails a set-your-password link. Courier vehicle, home zone and capacity are optional, but all three or none. `201`, or `409 EMAIL_ALREADY_REGISTERED`. |
| `PATCH /api/v1/admin/users/{userId}` | ADMIN | Enables or disables an account (disabling also ends its sessions) and replaces its staff roles. An admin cannot disable themselves (`422 CANNOT_DISABLE_YOURSELF`) or drop their own ADMIN role (`422 CANNOT_REMOVE_OWN_ADMIN`). |
| `POST /api/v1/admin/users/{userId}/invitation` | ADMIN | Sends the set-your-password e-mail again. `202`. |

A `MERCHANT_API` machine client gets `403`: it has no profile. Full contract:
[contracts/openapi/user-service.v1.yaml](../../contracts/openapi/user-service.v1.yaml).

## Design notes

- **Keycloak owns identity, user-service owns profile data.** `user_profile.id` is the Keycloak subject, so no
  password is stored here and no sign-up event is needed: the row is created just in time on the first
  `GET /users/me` (`INSERT ... ON CONFLICT DO NOTHING`, so simultaneous first requests are safe).
- **Roles are mirrored** into `user_role` from the token.
- **Keycloak decides who is a courier.** The courier directory reads the members of realm role COURIER through the
  Keycloak Admin API (`KeycloakIdentityDirectory`, service account `user-service-admin` with client credentials,
  secret `KC_USER_SERVICE_CLIENT_SECRET`), so couriers appear before their first sign-in. Listing role members
  needs the read-only `view-realm` permission in addition to `view-users`; both are in the realm file. If Keycloak
  cannot be reached the endpoint answers `503 IDENTITY_PROVIDER_UNAVAILABLE`.
- **Admin user management goes through Keycloak.** `KeycloakAdminAdapter` (port `IdentityAdmin`) creates accounts,
  sets realm roles, enables or disables them and logs them out, using the same service account with
  `manage-users`. SwiftDrop never chooses or sees a password: new staff get Keycloak's `UPDATE_PASSWORD` e-mail
  (valid 24 hours; locally it lands in Mailpit at `http://localhost:8026`). If a step after creating the Keycloak
  account fails, the account is deleted again, so a retry does not hit "already registered". If only the e-mail
  fails, the account stays and the answer says `invitationSent: false`. Only staff roles (COURIER, DISPATCHER,
  ADMIN) are managed here; CUSTOMER (every account's default role) and MERCHANT are never changed by these endpoints.
- **Address lines are encrypted at rest** with the same cipher as phone numbers; city and district stay readable
  because pricing and dispatch work with them. A partial unique index keeps one default address per person.
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
