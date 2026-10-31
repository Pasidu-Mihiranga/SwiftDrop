# SwiftDrop — Parcel Delivery & Courier Management Platform

Island-wide parcel delivery for Sri Lanka: customers book and track parcels live, partner online stores create
orders through an API and receive webhooks, dispatchers assign couriers, couriers update deliveries from their phones.

**Architecture:** microservices (Spring Boot · NestJS · FastAPI) behind an API gateway, Next.js web app as BFF,
Keycloak (OIDC + Google), Kafka (pub/sub + transactional outbox), PostgreSQL / MongoDB / Redis,
observability with Grafana · Loki · Tempo · Prometheus.

| Document | |
|---|---|
| [doc/01-architecture.md](doc/01-architecture.md) | Architecture, services, data model, security, flows, patterns |
| [doc/02-implementation-plan.md](doc/02-implementation-plan.md) | Phases, testing, DevOps, demo checklist |
| [doc/03-team-contribution.md](doc/03-team-contribution.md) | Who owns what (10 members, feature-by-feature) |

---

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| Docker Desktop | 4.x (≥ 8 GB RAM for the full stack) | infrastructure + containers |
| Java (Temurin) | 21 | Spring Boot services |
| Maven | 3.9+ | Java build |
| Node.js | 22.12+ | NestJS services, web app |
| pnpm | 10 (`corepack enable`) | Node workspace |
| uv | 0.11+ | Python AI service |

## Quick start

```bash
pnpm install            # Node workspace (web app, Nest services, shared lib)
pnpm env:init           # creates .env with freshly generated secrets (never commit it)
pnpm infra:up           # Postgres, Keycloak, Kafka (+topics), Mongo, Redis, RustFS, Mailpit, Kafka UI
pnpm obs:up             # optional: + Grafana, Loki, Tempo, Prometheus, Alloy
pnpm web:dev            # Next.js on http://localhost:3000
```

Run a service on your machine while the rest runs in Docker (it reads the root `.env` automatically):

```bash
mvn -pl services/parcel-service -am spring-boot:run           # Java service
mvn -pl services/api-gateway -am spring-boot:run              # gateway on :8080
pnpm --filter @swiftdrop/tracking-service dev                 # Nest service
cd services/ai-assistant-service && uv run uvicorn app.main:app --reload --port 8095
```

Everything in containers (first build takes a while):

```bash
pnpm stack:up           # profiles infra + observability + apps
pnpm stack:ps
pnpm stack:down
```

### Local URLs

| What | URL | Credentials |
|---|---|---|
| Web app | http://localhost:3000 | demo users below |
| API gateway | http://localhost:8080 | Bearer token |
| Keycloak admin | http://localhost:8180/admin | `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` from `.env` |
| Kafka UI | http://localhost:8088 | — |
| Mailpit (emails) | http://localhost:8026 | — |
| RustFS console (S3) | http://localhost:9011 | `S3_ACCESS_KEY` / `S3_SECRET_KEY` |
| Grafana | http://localhost:3001 | `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` |
| Prometheus | http://localhost:9090 | — |
| Postgres | localhost:15432 | per-service users, password `SWIFTDROP_DB_PASSWORD` |

Host ports are configurable in `.env` (defaults avoid clashing with other local stacks).

### Demo users (Keycloak realm `swiftdrop`)

Password for all: value of `KC_SEED_USER_PASSWORD` in `.env` (default `Swift@2026`).

| User | Role |
|---|---|
| admin@swiftdrop.lk | ADMIN |
| dispatcher@swiftdrop.lk | DISPATCHER |
| courier1@swiftdrop.lk, courier2@swiftdrop.lk | COURIER |
| customer@swiftdrop.lk | CUSTOMER |
| merchant@swiftdrop.lk | MERCHANT (merchant_id `00000000-0000-4000-8000-000000000001`) |

Machine client for the merchant API: `merchant-demo` (client credentials, secret `KC_DEMO_MERCHANT_CLIENT_SECRET`).

**Google login:** create an OAuth client in Google Cloud Console with redirect URI
`http://localhost:8180/realms/swiftdrop/broker/google/endpoint`, put `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
in `.env`, then re-import the realm: `pnpm keycloak:reimport`.

Get a token for API testing (dev-only `swiftdrop-test` client):

```bash
curl -s -d grant_type=password -d client_id=swiftdrop-test \
     -d username=customer@swiftdrop.lk -d password='Swift@2026' \
     http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token
curl -H "Authorization: Bearer <access_token>" http://localhost:8080/api/v1/parcels/meta/whoami
```

---

## Repository layout

```
apps/web/                 Next.js 16 (App Router) — all UIs + BFF (/api/bff/*), Auth.js + Keycloak
services/                 one folder per microservice
  api-gateway/            Spring Cloud Gateway — JWT validation, routing, CORS, correlation ids
  user-service/ merchant-service/ pricing-service/ parcel-service/ dispatch-service/ reporting-service/   (Spring Boot)
  tracking-service/ notification-service/ webhook-service/                                               (NestJS)
  ai-assistant-service/   FastAPI + MCP
libs/
  java/                   parent POM, common-logging, common-web, common-security, events, outbox
  node/swiftdrop-common/  event types + validation, Kafka consumer template, JWT, logger, Nest module
  python/swiftdrop_common/  logging, JWT, FastAPI problem+json
contracts/
  events/                 Kafka event JSON Schemas (+ examples) — the source of truth
  openapi/                REST contracts per service (v1)
infra/                    keycloak realm, postgres init, kafka topics, observability config, helm
tools/templates/          service templates used by `pnpm new-service`
scripts/                  env bootstrap, service generator
doc/                      architecture, plan, contribution sheet, diagrams, test reports
```

Every Java service has the same layers — `api` (presentation) → `application` (use cases) → `domain`
(plain Java) → `infrastructure` (adapters) — enforced by an ArchUnit test. Nest services mirror this per feature module.

### Adding a service

```bash
pnpm new-service --type java --name loyalty-service --port 8087 --route loyalty --db loyalty_db --db-user loyalty_svc --kafka
pnpm new-service --type nest --name chat-service --port 8093 --route chat --mongo
```

## Shared conventions (all stacks)

- **Auth:** every endpoint needs a Keycloak JWT (issuer + audience `swiftdrop-api` checked) unless explicitly public.
  Inject the caller as `AuthenticatedUser` (Java), `@CurrentUser()` (Nest) or `Depends(current_user)` (FastAPI).
- **Errors:** RFC 9457 `application/problem+json` with `code`, `traceId`, `timestamp`; never stack traces or personal data.
- **Correlation:** `X-Correlation-Id` is created at the gateway and copied into logs and events.
- **Logs:** one JSON object per line (`@timestamp, level, service, message, correlationId, traceId`); emails, phones,
  passwords and tokens are masked automatically.
- **Events:** published only through the outbox (`OutboxWriter.publish(...)`), consumed idempotently.
- **Config:** environment variables only; secrets live in `.env` (local) or Kubernetes Secrets.

## Tests

```bash
mvn verify                                   # Java: unit + Testcontainers ITs + JaCoCo gate (≥70%)
pnpm -r test                                 # Node libs/services/web (Jest/Vitest, ≥70%)
cd libs/python/swiftdrop_common && uv run pytest
cd services/ai-assistant-service && uv run pytest
```

## Git workflow

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. In short:

- `main` is protected: changes only through pull requests with one approving review and a green `no-emoji` check.
- Branch per feature: `feat/m<N>-<topic>`; Conventional Commits (`feat:`, `fix:`, `test:` …).
- **No emojis** anywhere in the repository, commits, pull requests or issues (`python scripts/check-no-emoji.py`).

## Container images

Every merge to `main` (and every `v*.*.*` tag) runs [`release.yml`](.github/workflows/release.yml): it builds one image per
service plus the web app, scans each with Trivy, and publishes it to the GitHub Container Registry:

```
ghcr.io/pasidu-mihiranga/swiftdrop-<service>:sha-<short-sha>   # immutable: pin this in Helm values / compose
ghcr.io/pasidu-mihiranga/swiftdrop-<service>:main              # newest build of main (also :latest)
ghcr.io/pasidu-mihiranga/swiftdrop-<service>:1.2.3             # on a v1.2.3 tag (also :1.2)
```

`<service>` is the folder name under `services/` (e.g. `reporting-service`), or `web` for `apps/web`.

- **Release gate:** an image with a *critical* vulnerability that already has a fix is not published. Fix it by bumping the
  base image or the dependency; if that is impossible for now, record the CVE with a reason in [`.trivyignore`](.trivyignore).
  Pull requests that change a Dockerfile run the same build and scan without publishing.
- **Supply chain:** published images carry OCI labels linking them to this repository, a provenance attestation and an SBOM.
- **Pulling:** GHCR creates new packages as private. After the first release, set each `swiftdrop-*` package to public
  (Package settings, Change visibility) so kind/minikube can pull without credentials, or create an image pull secret.
