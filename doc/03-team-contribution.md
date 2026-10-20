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

| # | Member name | Student ID | Feature | Backend delivered | Frontend delivered | Tests / coverage | Diagrams | PRs |
|---|---|---|---|---|---|---|---|---|
| M1 | | | | | | | | |
| M2 | | | | | | | | |
| M3 | | | | | | | | |
| M4 | | | | | | | | |
| M5 | | | | | | | | |
| M6 | | | | | | | | |
| M7 | | | | | | | | |
| M8 | | | | | | | | |
| M9 | | | | | | | | |
| M10 | | | | | | | | |
