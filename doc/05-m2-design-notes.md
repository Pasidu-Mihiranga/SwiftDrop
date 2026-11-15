# M2 design notes: patterns and principles applied (Issue #15)

Owner: Sasindu345 (M2) · Scope: `pricing-service`, fee-estimate UI, admin pricing UI, Helm/K8s

Companion to the diagrams in [`doc/diagrams/m2-pricing-zones`](diagrams/m2-pricing-zones/)
(see its [README](diagrams/m2-pricing-zones/README.md)). Everything below is claimed only where the
repository actually shows it; open items are marked.

## 1. Architectural style

Microservices behind an API gateway, with Next.js acting as the BFF and a database per
service — inherited from [01-architecture.md](01-architecture.md). `pricing-service` is
internally **layered / hexagonal**:

| Layer | Package | Responsibility |
|---|---|---|
| Presentation | `api` (`v1`, `v1.admin`, `dto`, `mapper`) | HTTP, Bean Validation, `@PreAuthorize`, problem+json |
| Business logic | `application` (use cases, `port/out`) and `domain` (model, policy) | Orchestration, invariants, the fee formula |
| Data | `infrastructure` (`persistence`, `redis`, `config`) | JPA adapters, `RedisQuoteStore`, injected `Clock` |

Dependencies point inwards only, and the `domain` package contains no Spring, JPA or
Hibernate type. This is not an aspiration: `LayeredArchitectureTest` asserts it with
ArchUnit and runs in the normal unit-test suite.

## 2. Integration patterns

| Pattern | Where M2 uses it | Why |
|---|---|---|
| **Request/reply (sync REST)** | `POST /api/v1/pricing/quotes`; `GET /api/v1/pricing/quotes/{quoteId}` intended for parcel-service (M3 handoff open) | A fee estimate is a question the user is waiting on; a booking cannot proceed without the answer |
| **API gateway + BFF (facade)** | Browser → Next.js BFF → `api-gateway` → service | One security choke point; the access token never reaches browser JavaScript, and the browser never learns a service hostname |
| **Stored snapshot / handoff token** | Redis holds the computed `QuoteSnapshot` under an opaque UUID for `PT15M` | The quote is *issued*, not recomputed. Lookup returns the stored snapshot verbatim, so an admin price change cannot retroactively alter a quote a customer was shown |
| **Publish/subscribe, broker** | **Not used by M2** | Pricing publishes no events. Pub/sub via Kafka belongs to the parcel and notification flows (M3/M4). Claiming it here would be inaccurate |

## 3. Design patterns

- **Strategy** — `PricingStrategy` is the single interface for "one way of pricing a
  parcel". `ZoneMatrixStrategy` charges the lane from the selected rate card;
  `WeightStrategy` charges chargeable weight (`ceil(weightKg)`).
- **Decorator** — `PricingDecorator` wraps another strategy and charges its own rule
  against the *rounded* total underneath it. `SizeSurchargeDecorator`,
  `ExpressSurchargeDecorator` and `CodFeeDecorator` are the three layers. The default
  chain is `COD( EXPRESS( SIZE( ZoneMatrix + Weight ) ) )`, so the fee formula is legible
  in the object graph rather than buried in an `if`-chain.
- **Composite** — `CompositePricingStrategy` runs base and weight in order and is itself
  a `PricingStrategy`, which is what lets the decorators treat "base + weight" as one
  thing.
- **Repository + Ports & Adapters** — five outbound ports under `application/port/out`
  (`ZoneRepository`, `DistrictAssignmentRepository`, `RateCardRepository`,
  `SizeClassRepository`, `QuoteStore`) are interfaces owned by the application layer;
  `infrastructure` supplies the JPA and Redis adapters.
- **Snapshot / value object** — `QuoteSnapshot` and `FeeBreakdown` are immutable records.
  `FeeBreakdown.plus(...)` returns a new breakdown instead of mutating one.
- **Builder-free immutability with a seam** — `FeeCalculator` has a second constructor
  taking an arbitrary pipeline, so a test can price with a different rule set without a
  mocking framework.
- **Temporal versioning (slowly-changing dimension)** — a rate card is never edited. A
  price change closes the card in force (`effective_to`) and inserts a new one; periods
  are half-open, so a card starting the day another ends leaves neither gap nor overlap,
  and `rejectOverlap` turns a genuine clash into `409 RATE_CARD_OVERLAP`.
- **Dependency injection of `Clock`** — `PricingClockConfiguration` supplies the clock, so
  quote expiry is asserted at the exact boundary instead of by sleeping in a test.

## 4. Principles

- **SRP** — `QuotePricingService` resolves coverage, `RateCardSelectionService` picks the
  card, `FeeCalculator` only assembles the chain, each rule charges one line.
- **OCP** — a new pricing rule is a new decorator plus an enum constant. No existing
  calculation is edited.
- **LSP** — `PricingDecorator.price(...)` is `final` and every rule honours the same
  contract: return a *new* breakdown, never mutate the argument.
- **DIP** — the application layer depends on its own port interfaces; adapters depend
  inwards. ArchUnit enforces the direction.
- **DRY** — district, zone, fee and multiplier literals live only in the Flyway seed
  migrations (`V5`, `V6`); none appear in Java or frontend code. The browser loads zones
  from the API rather than holding a copy. On the infrastructure side the Helm library
  chart serves the same purpose (Phase 4, open).
- **Fail fast and fail loudly** — an ambiguous rate card raises
  `422 AMBIGUOUS_RATE_CARD` rather than silently picking one. A district with no active
  zone raises `422 DISTRICT_NOT_SERVICED`.
- **Correct money arithmetic** — `BigDecimal`, scale 2, `HALF_UP`, applied once per
  breakdown line before it feeds the next rule, so the lines shown always reconcile with
  the total charged. A rule worth `0.00` is omitted rather than shown as a zero line.
- **Secure by default** — only `/api/v1/pricing/zones` and `POST /api/v1/pricing/quotes`
  are public (declared in `swiftdrop.security.public-paths` and the gateway allow-list).
  Every admin endpoint is `@PreAuthorize("hasRole('ADMIN')")` and is tested against
  CUSTOMER, COURIER, DISPATCHER, MERCHANT and anonymous callers. Unknown and expired
  quote identifiers both answer `404 QUOTE_NOT_FOUND`, so expiry is indistinguishable
  from a guess.

## 5. Known gaps (so the diagrams are not read as finished work)

- `deployment-m2-kubernetes.mmd` shows a **planned** topology. `infra/helm` currently
  holds only a README; Phase 4 is open.
- `diagrams/er-v1.mmd` still models `ZONE` with `code` as the primary key. The implemented schema
  uses surrogate `uuid` keys — see `er-m2-pricing-zones.mmd`. Reconciling it is
  an open Phase 5 item.
- The quote-to-booking handoff is unwired: the widget exposes `onQuote(quote)` carrying
  the `quoteId`, but M3 has no customer booking page yet.
