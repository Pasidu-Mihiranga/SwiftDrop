# Issue #15: M2 Pricing & Zones implementation plan

Owner: Sasindu345 (M2)  
Branch: `feat/m2-pricing-zones`  
Scope: pricing-service, fee estimate UI, admin pricing UI, Kubernetes/Helm

This checklist follows the [architecture](01-architecture.md),
[implementation plan](02-implementation-plan.md),
[team ownership](03-team-contribution.md), and
[`pricing-service` OpenAPI contract](../contracts/openapi/pricing-service.v1.yaml).
Complete it in small steps and keep checked items tied to actual repository work.
Each phase below is a review gate: finish it, report it, and wait for sign-off
before starting the next one.

## Phase 0: Recorded decisions

The questions that previously blocked this checklist are now settled. The table
is the authoritative record for the design PDF; the rows below it capture the
detail.

| Decision | Agreed answer |
|---|---|
| Zone scheme | Nine province zones (`WP`, `CP`, `SP`, `NP`, `EP`, `NWP`, `NCP`, `UP`, `SGP`) covering all 25 districts; 81 ordered rate-card pairs |
| Currency and arithmetic | LKR, `BigDecimal`, scale 2, `HALF_UP` applied per breakdown line so the lines sum exactly to the total |
| Fee formula and order | `BASE` + `WEIGHT`, then `SIZE`, then `EXPRESS`, then `COD` (see below) |
| Rate-card precedence | Newest `effective_from` that is still in force on the quote date wins; two matches is a configuration error, never a silent pick |
| Issued quotes vs admin edits | The quote is honoured. Redis holds the computed snapshot and lookup never recalculates; edits apply to new quotes only |
| Quote lookup contract | Full schema defined now in the OpenAPI contract and proposed to M3; unknown and expired both answer `404 QUOTE_NOT_FOUND` |
| Web approach | Client-side fee-estimate widget over the existing BFF path, with pure logic in `apps/web/src/lib` for the coverage gate |
| Helm scope | Library chart, umbrella chart and thin service charts, with Postgres, Redis and Keycloak as chart dependencies; Strimzi and RustFS documented as prerequisites |

- [x] Create the M2 feature branch and issue-specific agent guidance.
- [x] Currency, fee formula, operation order, precision and rounding agreed.
      Fees are LKR with two decimals. For a quote the service computes
      `subtotal = base_fee + per_kg_fee * ceil(weightKg)`, then
      `SIZE = subtotal * (size_class.multiplier - 1)`, then
      `EXPRESS = (subtotal + SIZE) * (express_multiplier - 1)`, then
      `COD = codAmount * cod_fee_pct`. The breakdown returns one line per rule
      in the order `BASE`, `WEIGHT`, `SIZE`, `EXPRESS`, `COD`; omitted rules are
      omitted rather than returned as zero. Every line is rounded to two
      decimals with `HALF_UP` before the total is summed, so the breakdown
      always reconciles with `fee`.
- [x] Rate-card precedence and the effect of edits on issued quotes agreed.
      A rate card applies when `effective_from <= quoteDate` and
      `effective_to` is null or after the quote date; if several rows match, the
      latest `effective_from` wins and an exact tie is rejected as ambiguous
      rather than resolved arbitrarily. Admin edits close the current row by
      setting `effective_to` and insert a new row; rows are never hard-deleted.
      Quotes already issued keep the fee they were given, because the Redis
      snapshot is returned verbatim and never recomputed.
- [x] Zone codes and the authoritative 25-district mapping agreed. The nine
      zones follow the provinces and are stored as reviewed seed data in the
      pricing database, not in Java or frontend code:

      | Code | Zone | Districts |
      |---|---|---|
      | `WP` | Western | Colombo, Gampaha, Kalutara |
      | `CP` | Central | Kandy, Matale, Nuwara Eliya |
      | `SP` | Southern | Galle, Matara, Hambantota |
      | `NP` | Northern | Jaffna, Kilinochchi, Mannar, Mullaitivu, Vavuniya |
      | `EP` | Eastern | Ampara, Batticaloa, Trincomalee |
      | `NWP` | North Western | Kurunegala, Puttalam |
      | `NCP` | North Central | Anuradhapura, Polonnaruwa |
      | `UP` | Uva | Badulla, Monaragala |
      | `SGP` | Sabaragamuwa | Kegalle, Ratnapura |

- [x] Quote lookup response, authentication, expiry behavior and the fields
      parcel-service validates are defined in the contract. `GET
      /api/v1/pricing/quotes/{quoteId}` requires a bearer token, returns the
      stored snapshot (fee, breakdown, zones, districts, weight, size class,
      service level, COD amount, issue and expiry times) and answers `404
      QUOTE_NOT_FOUND` for both unknown and expired identifiers so expiry is not
      distinguishable from a guess. The response is derived only from the stored
      snapshot; request data is never substituted into it. **Proposed to M3 and
      awaiting the parcel-service owner's confirmation.**
- [x] Draft API gaps resolved in `contracts/openapi/pricing-service.v1.yaml`:
      admin zone create/update, district assignment, rate-card create/close,
      size-class read/update, request and response schemas, and problem+json
      error responses for every endpoint.

## Phase 1: Pricing data and zones

- [x] Add a new Flyway migration for `zone` and `district_zone`, including
      keys, referential integrity, and the zone lookup index.
- [x] Add migrations for `size_class` and `rate_card`, including sensible
      uniqueness, effective-date, and nonnegative-fee constraints.
      `V3__create_size_class.sql` and `V4__create_rate_card.sql`.
- [x] Add reviewed seed migrations for the nine zones, all 25 district
      assignments, the four size classes, and the 81 rate-card pairs. Keep every
      fee and district literal in SQL; none may appear in Java or frontend code.
      `V5__seed_zones_and_districts.sql` and
      `V6__seed_size_classes_and_rate_cards.sql`. Rate cards are priced by the
      number of province borders a parcel crosses, computed as the shortest path
      over an adjacency list declared in the seed file, so a one-province trip
      such as Colombo to Kandy is not charged the same as Colombo to Jaffna.
      Both seed files are idempotent and the matrix is symmetric.
- [x] Add domain models and persistence adapters for zones, district mapping,
      size classes, and rate cards using the existing layered package layout.
      The `domain` layer stays free of Spring, JPA and Hibernate types.
- [x] Track the four outbound repository interfaces under `application/port/out`
      by exempting that source directory from the generic `out/` ignore rule.
      A clean Maven compile succeeds with the interfaces present.
- [x] Implement public active-zone lookup and admin zone/district maintenance,
      and declare the public pricing paths in the service's own
      `swiftdrop.security.public-paths`.
- [x] Cover persistence, the seeded row counts, and invalid/duplicate mapping
      behavior, plus anonymous read and non-admin rejection on the controllers.
      71 tests green (52 unit including ArchUnit, 19 integration on
      Testcontainers Postgres and Redis); JaCoCo line coverage 86 per cent.

## Phase 2: Quotes and rate administration

- [x] Implement deterministic rate-card selection for two active zones and
      the quote date. Reject missing or ambiguous rates clearly.
- [x] Implement pricing rules as domain strategies/decorators with a monetary
      breakdown. Use decimal arithmetic and the agreed rounding policy.
      `PricingStrategy` is the common interface. `ZoneMatrixStrategy` and
      `WeightStrategy` charge the lane and the weight; `SizeSurchargeDecorator`,
      `ExpressSurchargeDecorator` and `CodFeeDecorator` layer the surcharges on
      top, each charging against the rounded total beneath it. `FeeCalculator`
      only assembles the chain, so the formula is visible in the object graph and
      adding a rule means adding a decorator rather than editing a calculation.
- [x] Validate weight, size class, service level, COD amount, districts, and
      active coverage at the API boundary.
- [x] Store quote data in Redis under an opaque ID with a configurable
      15-minute TTL; return the ID, fee, breakdown, zones, and expiry.
- [x] Implement quote lookup for parcel-service validation. Define 404 for
      unknown/expired quotes and prevent request data from being substituted.
- [x] Implement ADMIN-only zone, rate-card, and size-class management APIs.
      Rate cards are versioned rather than edited: a price change closes the card
      in force by setting `effective_to` and adds a new one, and overlapping
      periods for a lane are rejected. Effective periods are half-open, so a card
      starting the day another ends leaves neither a gap nor an overlap.
      Listing supports zone and effective-date filters with the shared pagination
      envelope.
- [x] Keep OpenAPI and generated Swagger behavior aligned with each endpoint.
      All twelve contract paths are implemented, and every documented status code
      has a test asserting the problem+json `code`.
- [x] Cover pricing edge cases, quote expiry, and admin authorization.
      171 tests green (140 unit including ArchUnit, 31 integration on
      Testcontainers Postgres and Redis); JaCoCo coverage 96.2 per cent line
      and 89.5 per cent branch.
      Quote expiry is driven by a controlled clock so the boundary is asserted
      exactly rather than by waiting, and every admin endpoint is checked against
      CUSTOMER, COURIER, DISPATCHER, MERCHANT and anonymous callers.

## Phase 3: Web flows

- [x] Add a public `/quote` page and reusable fee-estimate widget with loading,
      validation, empty, expired, and service-error states.
- [x] Load zones from the pricing API through the existing BFF/gateway path.
      Never duplicate zone lists, rates, or API hostnames in the browser.
- [x] Add admin zone/district and rate-card views with create/edit flows,
      effective-date visibility, and permission-aware navigation. Pricing
      administration is ADMIN-only, so it needs a narrower route rule than the
      shared `/admin` prefix.
- [x] Reuse the existing web design system and CSS color variables; keep
      controls accessible on phone and desktop viewports. Pricing notices and
      status labels use shared Tailwind utilities, narrow layouts keep form
      controls usable, and validation and action failures are announced.
- [x] Keep testable pricing logic in `apps/web/src/lib` so it counts toward the
      web coverage gate, and add the component-test setup the widget needs.
- [ ] Hand the quote ID to the customer booking flow using the agreed M3
      contract, without taking ownership of parcel booking logic.
      The fee widget exposes `onQuote(quote)`, whose `quoteId` is required by
      `CreateParcelRequest` in `contracts/openapi/parcel-service.v1.yaml`.
      M3 has not added a customer booking page or confirmed its route yet, so
      there is no destination to wire without creating a broken journey.

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
      Kubernetes deployment diagram under `doc/diagrams`, and reconcile
      `doc/diagrams/er-v1.mmd` with the surrogate-key pricing schema.
- [ ] Add a pricing Grafana panel/metric and update service/Helm README docs.
- [ ] Demonstrate an anonymous estimate, an admin rate change, quote expiry,
      and a valid quote being consumed by parcel-service.
- [ ] Complete the M2 row in `doc/03-team-contribution.md` with delivered work.
- [ ] Request review when the feature is ready. Do not commit or push until
      the user explicitly asks; do not add an agent co-author trailer.

## Current boundary

The pricing service is feature complete behind the API: the seeded schema, the
public coverage list, the Strategy and Decorator fee pipeline, Redis-backed
quotes with a configurable lifetime, authenticated quote lookup for M3, and
ADMIN maintenance of zones, districts, rate cards and size classes. Every path in
the OpenAPI contract is implemented.

The `/quote` page, fee widget, and admin pricing screens are implemented. The
pricing UI uses shared status styles and responsive layouts; web type checking,
80 tests, and a production webpack build pass. A live browser viewport review
has not been possible in this environment. The native Turbopack build cannot
bind its worker port here, so it has not been verified. The quote-to-booking
handoff remains open until M3 provides a booking page and confirms the
quote-lookup response shape. Helm charts in `infra/helm` and the delivery items
remain open.

Note for local runs: this machine's active Docker context is Colima, which
Testcontainers does not discover on its own. Integration tests need
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` in the environment.
