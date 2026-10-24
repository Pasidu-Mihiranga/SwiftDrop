# Issue #15: M2 Pricing & Zones implementation plan

Owner: Sasindu345 (M2)  
Branch: `feat/m2-pricing-zones`  
Scope: pricing-service, fee estimate UI, admin pricing UI, Kubernetes/Helm

This checklist follows the [architecture](01-architecture.md),
[implementation plan](02-implementation-plan.md),
[team ownership](03-team-contribution.md), and
[`pricing-service` OpenAPI contract](../contracts/openapi/pricing-service.v1.yaml).
Complete it in small steps and keep checked items tied to actual repository work.

## Before pricing rules

- [x] Create the M2 feature branch and issue-specific agent guidance.
- [ ] Agree on the currency, fee formula and operation order (base, weight,
      size, express, COD), decimal precision, and rounding with the team.
- [ ] Agree on rate-card precedence for effective dates and how edits affect
      quotes already issued.
- [ ] Agree on zone codes and the authoritative mapping of all 25 Sri Lankan
      districts. Store those assignments as reviewed seed/configuration data.
- [ ] Confirm with M3 the quote lookup response, authentication, expiry
      behavior, and fields the parcel service must validate at booking.
- [ ] Resolve draft API gaps for admin updates, district assignments, size
      class management, error responses, and request/response schemas.

## Phase 1: Pricing data and zones

- [x] Add a new Flyway migration for `zone` and `district_zone`, including
      keys, referential integrity, and the zone lookup index.
- [ ] Add migrations for `size_class` and `rate_card`, including sensible
      uniqueness, effective-date, and nonnegative-fee constraints.
- [ ] Add domain models and persistence adapters for zones, district mapping,
      size classes, and rate cards using the existing layered package layout.
- [ ] Add reviewed zone and 25-district seed/configuration data; do not place
      these assignments in frontend code.
- [ ] Implement public active-zone lookup and admin zone/district maintenance.
- [ ] Cover persistence and invalid/duplicate mapping behavior.

## Phase 2: Quotes and rate administration

- [ ] Implement deterministic rate-card selection for two active zones and
      the quote date. Reject missing or ambiguous rates clearly.
- [ ] Implement pricing rules as domain strategies/decorators with a monetary
      breakdown. Use decimal arithmetic and the agreed rounding policy.
- [ ] Validate weight, size class, service level, COD amount, districts, and
      active coverage at the API boundary.
- [ ] Store quote data in Redis under an opaque ID with a configurable
      15-minute TTL; return the ID, fee, breakdown, zones, and expiry.
- [ ] Implement quote lookup for parcel-service validation. Define 404 for
      unknown/expired quotes and prevent request data from being substituted.
- [ ] Implement ADMIN-only zone, rate-card, and size-class management APIs.
- [ ] Keep OpenAPI and generated Swagger behavior aligned with each endpoint.
- [ ] Cover pricing edge cases, quote expiry, and admin authorization.

## Phase 3: Web flows

- [ ] Add a public `/quote` page and reusable fee-estimate widget with loading,
      validation, empty, expired, and service-error states.
- [ ] Load zones from the pricing API through the existing BFF/gateway path.
      Never duplicate zone lists, rates, or API hostnames in the browser.
- [ ] Add admin zone/district and rate-card views with create/edit flows,
      effective-date visibility, and permission-aware navigation.
- [ ] Reuse the existing web design system and CSS color variables; keep
      controls accessible on phone and desktop viewports.
- [ ] Hand the quote ID to the customer booking flow using the agreed M3
      contract, without taking ownership of parcel booking logic.

## Phase 4: Kubernetes and Helm

- [ ] Create `infra/helm/swiftdrop-service` as the reusable library chart for
      Deployment, Service, ConfigMap, Secret references, probes, and optional HPA.
- [ ] Create `infra/helm/swiftdrop` as the umbrella chart and thin service
      charts/values for the application services, including pricing-service.
- [ ] Provide dev and production values for images, ports, resources,
      dependencies, and externalized secrets; keep credentials out of Git.
- [ ] Configure Ingress for web, API, auth, and Grafana as specified in the
      architecture; use environment-specific hosts and TLS settings.
- [ ] Document installation on kind/minikube, dependency setup, and required
      image/secrets configuration.
- [ ] Run Helm lint/template and a kind smoke deployment when the required
      container images and cluster are available.

## Phase 5: Delivery

- [ ] Add focused unit and integration coverage for calculations, persistence,
      expiry, authorization, and the web workflows; meet the project 70% gate.
- [ ] Add pricing/zones ER detail, pricing strategy class diagram, and the
      Kubernetes deployment diagram under `doc/diagrams`.
- [ ] Add a pricing Grafana panel/metric and update service/Helm README docs.
- [ ] Demonstrate an anonymous estimate, an admin rate change, quote expiry,
      and a valid quote being consumed by parcel-service.
- [ ] Complete the M2 row in `doc/03-team-contribution.md` with delivered work.
- [ ] Request review when the feature is ready. Do not commit or push until
      the user explicitly asks; do not add an agent co-author trailer.

## Current boundary

The repository currently has the pricing service scaffold, gateway/Compose
wiring, Dockerfile, and draft API contract. Pricing business endpoints, the
remaining tables, web pages, and Helm charts are still to be built. The first
code step on this branch establishes the zone schema only; repositories, rates,
and district assignments remain for later steps.
