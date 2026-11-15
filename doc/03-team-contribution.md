# SwiftDrop — Team Split & Contribution Sheet

> **SwiftDrop docs:** [01 Architecture](01-architecture.md) · [02 Implementation Plan](02-implementation-plan.md) · [03 Team & Contribution](03-team-contribution.md)

## 12. Team Split — 10 Members, Feature-by-Feature, Equal Frontend + Backend

Each member owns **one vertical feature end-to-end**: backend service/module + frontend pages + tests + Dockerfile/Helm values + Grafana panel + their own diagrams, and presents that feature. One cross-cutting platform task is spread across members so nobody does only infrastructure.

| # | Feature | Backend | Frontend | Platform share | Diagrams owned |
|---|---|---|---|---|---|
| M1 | **Identity & Access** | Keycloak realm (roles, clients, Google IdP, mappers), `api-gateway` (routes, JWT, rate limit, CORS, WS), `user-service` (JIT profile, addresses, courier profiles, admin user mgmt) | Auth.js setup, login/signup/Google button, `proxy.ts` role guards, profile & address book, admin user management | CI base workflows, root README | OAuth sequence, ER users/roles, customer use case |
| M2 | **Pricing & Zones** | `pricing-service` (zones, district map seed for 25 districts, rate cards, Strategy/Decorator, Redis quotes) | Fee-estimate widget, public `/quote` page, admin zones & rate-card editor | **Helm library + umbrella chart, K8s/Ingress** | ER pricing/zones, Strategy class diagram, deployment diagram |
| M3 | **Customer Booking** | `parcel-service` core: create parcel, quote validation (Resilience4j), tracking-number factory, customer queries, cancel; **`swiftdrop-outbox` + `swiftdrop-events` libs** | Multi-step create-delivery wizard, my-parcels list, parcel detail | Java parent POM and common libs | Create-delivery sequence, ER parcels |
| M4 | **Courier Ops & Status Lifecycle** | `parcel-service` status module: State machine, history, proof (S3 presigned, RustFS locally), failure reasons, `ParcelAccessPolicy` + authorization tests | Mobile-first courier app: today's run sheet, status stepper, photo capture/upload, failed-delivery form | Object storage (RustFS) setup | Parcel state diagram, courier use case, status-update sequence |
| M5 | **Dispatch & Workload** | `dispatch-service`: unassigned read model, assign/reassign, run sheet, workload projection, outbox `parcel-assigned` | Dispatcher board (filter by zone, assign modal showing courier load), workload dashboard | Kafka topic scripts, Kafka UI | Dispatcher use case, assignment activity diagram |
| M6 | **Real-time Tracking** | `tracking-service` (NestJS): consumers → Mongo timeline, Socket.IO with JWT handshake + Redis adapter, public masked API | Public mobile tracking page, live timeline component, live toasts on customer/merchant dashboards, live dispatcher board updates | **`libs/node/swiftdrop-common`** (Kafka, guard, logger) | Real-time tracking sequence |
| M7 | **Merchant Onboarding & API** | `merchant-service`: registration, approval workflow, Keycloak client provisioning/rotation; `MerchantOrderController` + idempotency in parcel-service | Merchant registration, pending/approved state, credentials page (secret shown once), merchant orders dashboard (filters, CSV export), admin approval queue | OpenAPI aggregation at the gateway, `contracts/openapi` | Merchant use case, merchant API sequence |
| M8 | **Webhooks & External Integration** | `webhook-service`: webhook subscriptions + secrets, HMAC signing, BullMQ retry/backoff, DLQ topic, delivery log, replay | Merchant webhook settings (URL, events, test ping), delivery-log viewer, admin dead-letter replay | **`tools/merchant-simulator`** | Integration design chapter, webhook retry activity diagram |
| M9 | **Notifications, History & Receipts** | `notification-service`: consumers, Email/SMS channel Strategy, Handlebars templates, preferences, log; receipt PDF endpoint (OpenPDF) in parcel-service | Delivery history (filters, pagination), receipt download, notification bell/centre, preferences, demo SMS inbox | **Observability stack** (Alloy, Loki, Tempo, Prometheus, Grafana dashboards) | Notification flow, observability diagram |
| M10 | **Reporting & AI Assistant** | `reporting-service` (projections, report APIs); `ai-assistant-service` (FastAPI, MCP tools, LLM strategy, guardrails) | Admin reports dashboard (deliveries/day, success rate, per zone/courier charts), chat-assistant widget with streaming | `release.yml` image build/push, Playwright E2E harness | Architecture diagram, AI/MCP sequence |

**Shared rules:**
- Everyone writes unit + integration tests for their own code (≥70%).
- Everyone adds their own topics to `contracts/events`, their own OpenAPI spec, and their own section in the design PDF.
- Feature branches are named `feat/m<N>-<topic>`.
- `doc/03-team-contribution.md` doubles as the contribution sheet.

---

---

## Contribution Sheet (fill in for submission)

M2's platform share (the Helm library chart, the umbrella chart and Kubernetes/Ingress) is
not a column in this table. It is delivered in `infra/helm`: a library chart, twelve thin
service charts, an umbrella chart with the dev datastores and one Ingress, dev and
production value overlays, `.github/workflows/helm.yml`, and the kind install scripts. The
dev slice was deployed to a kind cluster and exercised end to end; what that proved and
what it did not is recorded in `doc/04-m2-pricing-zones-issue-15.md`.


| # | Member name | Student ID | Feature | Backend delivered | Frontend delivered | Tests / coverage | Diagrams | PRs |
|---|---|---|---|---|---|---|---|---|
| M1 | | | | | | | | |
| M2 | wijewardana S.J. | TODO: add student ID | **Pricing & Zones** | `pricing-service`: 6 Flyway migrations (zone, district_zone, size_class, rate_card) with reviewed seed data for 9 province zones, all 25 districts, 4 size classes and 81 rate-card pairs priced by province borders crossed; Strategy + Decorator fee pipeline (`ZoneMatrixStrategy`, `WeightStrategy`, size/express/COD decorators) with BigDecimal HALF_UP per line; deterministic rate-card selection; Redis-backed quotes with a configurable TTL; authenticated quote lookup for M3; ADMIN zone, district, rate-card and size-class APIs with half-open versioning; custom Micrometer meters behind a `PricingMetrics` port. All 12 OpenAPI paths implemented | Public `/quote` page and reusable fee-estimate widget (loading, validation, empty, expired and error states, countdown, `onQuote` handoff); ADMIN-only zones/districts and rate-card screens with effective-date visibility; server actions with problem+json field errors; zones loaded through the existing BFF path, no duplicated zone lists or hostnames in the browser | Java 181 tests (150 unit incl. ArchUnit, 31 Testcontainers integration), JaCoCo 96.34% lines / 89.47% branches. Web 117 tests, 99.72% lines / 90.27% branches. Both above the 70% gate; reports collected in `doc/test-reports/` | ER pricing/zones, Strategy class diagram, Kubernetes deployment diagram, plus use case, layered architecture, integration, 2 activity and 3 sequence diagrams; reconciled `er-v1.mmd` with the surrogate-key schema | branch `feat/m2-pricing-zones`, 28 commits; PR to be opened |
| M3 | | | | | | | | |
| M4 | | | | | | | | |
| M5 | | | | | | | | |
| M6 | | | | | | | | |
| M7 | | | | | | | | |
| M8 | | | | | | | | |
| M9 | | | | | | | | |
| M10 | | | | | | | | |
