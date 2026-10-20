# SwiftDrop — Implementation Plan

> **SwiftDrop docs:** [01 Architecture](01-architecture.md) · [02 Implementation Plan](02-implementation-plan.md) · [03 Team & Contribution](03-team-contribution.md)

Architecture, data model, security and flows are in [01-architecture.md](01-architecture.md). Member ownership is in [03-team-contribution.md](03-team-contribution.md).

---

## 10. Containerisation, Kubernetes, CI/CD

- **Dockerfiles:** multi-stage builds, non-root users, healthchecks.
  - Java: Temurin 21 JRE, layered jar, `-XX:MaxRAMPercentage=70`, virtual threads.
  - Node: `node:22-alpine`, pnpm deploy.
  - Python: `python:3.12-slim` with uv.
- **docker-compose.yml** with profiles: `infra` (Kafka, Keycloak, DBs, RustFS, Mailpit, Kafka UI), `observability` (LGTM + Alloy), `apps` (all services + web + simulator). Init containers handle topic creation and the Keycloak realm import. `pnpm stack:up` starts everything. Recommend ≥16 GB RAM; members can run only `infra` plus their own service locally.
- **Kubernetes (kind/minikube):**
  - Umbrella Helm chart; a library chart supplies Deployment, Service, ConfigMap, Secret, HPA and probes.
  - Ingress (ingress-nginx) hosts: `swiftdrop.local`, `api.swiftdrop.local`, `auth.swiftdrop.local`, `grafana.swiftdrop.local`.
  - Kafka via the **Strimzi** operator (KRaft); Postgres/Mongo/Redis/RustFS as StatefulSets with PVCs; Keycloak via the official operator or a Deployment.
  - HPA on tracking-service and parcel-service; LGTM through the Grafana Helm charts.
- **GitHub Actions:**
  - Path-filtered CI per language (build, lint, unit + integration tests with Testcontainers, coverage upload as an artifact, JaCoCo gate ≥70%).
  - `release.yml` builds and pushes images to GHCR on `main`, tagged with the SHA.
  - `helm.yml` runs lint + template, plus a kind smoke deploy.
  - Trivy image scan and Dependabot.
  - Branch protection: PR + 1 review + green CI. Conventional Commits. CODEOWNERS per service folder.

---

## 11. Testing Strategy (15 marks)

| Level | Java | Node | Python | Web |
|---|---|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ (state machine, pricing strategies, access policy) | Jest (signer, retry policy, masking) | pytest (tool functions, provider factory with a fake LLM) | Vitest + RTL (forms, guards) |
| Integration | Spring Boot Test + Testcontainers (Postgres, Kafka, Keycloak via `testcontainers-keycloak`), `spring-security-test` JWT | testcontainers-node (Kafka, Mongo, Redis), supertest, nock | respx | — |
| Event flow (required) | parcel-service: status update → outbox → Kafka → Awaitility consumer asserts the envelope matches the JSON Schema | webhook-service: Kafka event → signed POST to nock; 500 response → retries → DLQ | — | — |
| Authorization (required) | courier B gets 404 on courier A's parcel; customer can't read another's parcel; merchant isolation; non-admin can't approve merchants or edit pricing | non-owner socket can't join a private room | anonymous assistant can't call private tools | middleware redirects for wrong role |
| Architecture | ArchUnit layer rules | — | — | — |
| E2E | Playwright on the compose stack: book → assign → courier updates → live tracking + Mailpit email + simulator webhook | | | |

Coverage: JaCoCo, Istanbul/lcov and pytest-cov, with a **≥70% line target per service**. CI publishes HTML reports; a script collects summaries into `doc/test-reports/` for submission.

---

## 13. Phased Implementation Timeline (8 one-week sprints; adjust to the deadline)

| Phase | Weeks | Goal | Exit criteria |
|---|---|---|---|
| **0. Foundations** | W1 | Monorepo scaffold, parent POM, pnpm workspace, shared libs skeletons, `docker-compose` infra profile (Kafka, Keycloak + realm import, Postgres init, Mongo, Redis, RustFS, Mailpit), `.env.example`, CI skeleton. Freeze contracts: event JSON Schemas, OpenAPI drafts, ER v1. Each member drafts their use-case and sequence diagrams. | `pnpm infra:up` works; login through Keycloak works; Next.js shell with role routing; contracts merged |
| **1. Core sync MVP** | W2–W3 | Every service runs its happy-path REST with JWT: profiles, quotes, create parcel, merchant register/approve, assign courier, courier status update (sync), public tracking (DB read), basic pages per feature | Demo the end-to-end flow without Kafka (dispatch reading parcels through REST is temporary) |
| **2. Event-driven integration** | W4–W5 | Outbox relay → Kafka; all consumers (tracking, notification, webhook, dispatch, reporting); Socket.IO live updates; webhooks with retry/DLQ; merchant client-credentials API + simulator; idempotent consumers | Status change visible live in the browser, email in Mailpit, mock SMS, webhook in the simulator, retry demonstrated |
| **3. Hardening** | W6 | PII encryption + log masking, ProblemDetail everywhere, rate limiting, input validation audit, authorization test suite, observability (LGTM dashboards, traces across Kafka), coverage ≥70%, ArchUnit | Security checklist done; coverage gate green in CI; Grafana shows the trace for a status update |
| **4. Bonus** | W7 | Helm charts + kind deployment with Ingress/ConfigMaps/Secrets; AI assistant + MCP + chat widget; release pipeline to GHCR | `helm install` brings up the full system; the assistant answers "Where is SD…?" and "How much for 2 kg to Kandy?" |
| **5. Delivery** | W8 | Design PDF (all diagrams), README setup guide, test reports in `doc/test-reports/`, Playwright demo script, contribution sheet, demo rehearsal (each member presents their feature) | Submission package complete |

**Definition of Done (per feature):** code + tests (≥70%) + Dockerfile + compose entry + Helm values + OpenAPI/event schema + Grafana panel + diagram + PR reviewed + README section.

---

## Phase 0 status — delivered

| Area | Delivered |
|---|---|
| Monorepo | Maven aggregator + `swiftdrop-parent` (Boot 4.0.8, Spring Cloud 2025.1.3, JaCoCo gate 70%); pnpm workspace; uv projects; `.editorconfig`, `.gitattributes`, `.dockerignore` |
| Shared libs (Java) | `common-logging` (JSON layout + PII masking), `common-web` (problem+json, correlation id, API exceptions), `common-security` (Keycloak JWT, roles, `AuthenticatedUser`, 401/403 problems), `events` (records for every contract), `outbox` (transactional outbox + relay + idempotent consumer + Flyway migrations) |
| Shared libs (Node/Python) | `@swiftdrop/common` (event types + JSON-Schema validation, Kafka consumer template with retries/DLQ, Mongo idempotency, JWT, pino logger with masking, Nest module: guard, problem filter, metrics); `swiftdrop_common` (FastAPI equivalents) |
| Services | All 11 services scaffolded with the layered structure, security, logging, metrics, Dockerfile, README and a `/meta/whoami` smoke endpoint; generated from `tools/templates/*` via `pnpm new-service` |
| Web | Next.js 16 + Auth.js v5 (Keycloak OIDC + PKCE, Google via `kc_idp_hint`), tokens server-side only, BFF proxy `/api/bff/*`, role-based `proxy.ts`, role dashboards with a live platform check |
| Infra | `docker-compose.yml` with `infra` / `observability` / `apps` profiles; Postgres init (DB + role per service); Keycloak realm (roles, clients, Google IdP, demo users, `merchant_id` attribute); Kafka topics; LGTM config + starter Grafana dashboard |
| Contracts | Event JSON Schemas + examples (validated by Java and Node tests), OpenAPI v1 drafts for every service, ER v1 (`doc/diagrams/er-v1.mmd`) |
| CI | GitHub Actions for Java, Node/web, Python and platform config; CODEOWNERS, PR template, Dependabot |

**Phase 0 exit criteria — verified**
- `pnpm infra:up` (+ `obs:up`): all containers healthy; 7 databases/roles created; 6 Kafka topics; realm imported; Loki receives JSON logs; Grafana datasources + dashboard provisioned.
- Login through Keycloak: scripted browser flow (Auth.js → Keycloak PKCE → callback) lands each role on its home page; the browser session contains roles but no tokens; wrong-role pages redirect to `/forbidden`.
- End-to-end JWT chain: web (server/BFF) → gateway → parcel-service returns the caller's identity; no token → 401; tampered token rejected by the service itself.
- Tests: Java 6 libs + gateway + 6 services incl. Testcontainers ITs (outbox → Kafka event flow, each service boots against real Postgres/Kafka/Redis) — all green with JaCoCo ≥70%; Node 58 tests; web 12; Python 25.
- Images: one per template type built successfully (Java, Nest, Python, Next.js); the Java image runs in compose and validates tokens via the internal Keycloak URL.

**Design decisions taken in Phase 0** (already reflected in 01-architecture.md):
1. **RustFS replaces MinIO** — MinIO stopped publishing community images; RustFS is S3-compatible (Apache-2.0).
2. **webhook-service owns webhook subscriptions and secrets** — HMAC secrets never travel over Kafka; the compacted `merchant.webhook-config` topic was dropped.
3. **Route prefixes fixed** — `/api/v1/admin/pricing/**`, `/api/v1/reports/**`, `/api/v1/webhooks/**`, `/api/v1/tracking/public/{trackingNumber}`.
4. **Configurable host ports** — defaults avoid clashes with other local stacks and Windows' reserved port ranges (Postgres 15432, Redis 6380, Mongo 27018, S3 9010/9011, Mailpit 8026/1026).
5. **Separate `swiftdrop-common-logging` module** so the reactive gateway can share logging without pulling in Spring MVC.

---

## 14. Critical Files to Create First (Phase 0)

- `docker-compose.yml`, `.env.example`, `Makefile`
- `infra/keycloak/realm-swiftdrop.json`, `infra/postgres/init/01-create-databases.sh`, `infra/kafka/create-topics.sh`
- `pom.xml`, `libs/java/swiftdrop-parent/pom.xml`, `libs/java/swiftdrop-common-logging`, `swiftdrop-common-web`, `swiftdrop-common-security`, `swiftdrop-events`, `swiftdrop-outbox`
- `pnpm-workspace.yaml`, `libs/node/swiftdrop-common`
- `contracts/events/*.schema.json`, `contracts/openapi/*.v1.yaml`
- `apps/web` (Auth.js + Keycloak, BFF proxy, middleware)
- `.github/workflows/ci-*.yml`, `.github/CODEOWNERS`

---

## 15. Verification (end-to-end demo checklist)

1. `make up`: all containers healthy (`docker compose ps`); Kafka UI shows the topics; Keycloak realm is imported.
2. Customer signs in with Google, gets a quote, creates a parcel, and receives a tracking number + Mailpit email.
3. Merchant registers → admin approves → merchant copies client credentials → `tools/merchant-simulator` gets a token, POSTs an order (repeated Idempotency-Key returns the same parcel), and receives the signed `parcel.created` webhook.
4. Dispatcher sees the parcel in the unassigned board (live), assigns it to a courier, and the workload count increments.
5. Courier updates PICKED_UP → … → DELIVERED with a photo. The public `/track/<no>` page updates live on a phone-sized viewport. Email + mock SMS arrive and the merchant webhook fires. Stopping the simulator shows retries; after the DLQ, an admin replays the delivery.
6. Courier B tries courier A's parcel and gets 404 (also covered by an automated test).
7. Admin reports show deliveries/day and success rate. Grafana shows one trace spanning gateway → parcel → Kafka → webhook, with linked Loki logs.
8. `mvn verify`, `pnpm -r test --coverage` and `pytest --cov` all pass with ≥70%; reports are copied to `doc/test-reports/`.
9. `helm install swiftdrop infra/helm/swiftdrop -f values-dev.yaml` on kind: pods ready and the Ingress hosts reachable.
10. AI assistant: "Where is my parcel SD…?" → tool call → correct status; "How much to send 2 kg to Kandy?" → quote from pricing-service.
