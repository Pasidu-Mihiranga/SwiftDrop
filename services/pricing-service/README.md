# pricing-service

Delivery zones, rate cards and fee quotes (Strategy + Decorator pricing pipeline).

| | |
|---|---|
| Port | `8083` |
| Database | PostgreSQL `pricing_db` (user `pricing_svc`) |
| API base | `/api/v1/pricing` (through the gateway at `http://localhost:8080`) |
| Swagger UI | `http://localhost:8083/swagger-ui.html` |
| Owner | see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Package layout

```
lk.swiftdrop.pricing
├── api/              PRESENTATION — controllers (v1/), DTOs (dto/), mappers (mapper/)
├── application/      USE CASES — command/, query/, port/out/ (interfaces to the outside world)
├── domain/           BUSINESS MODEL — model/, policy/, exception/ (plain Java, no Spring/JPA)
└── infrastructure/   ADAPTERS — persistence/, messaging/, client/, config/
```

The rules are enforced by `LayeredArchitectureTest` (ArchUnit). Read each layer's `package-info.java`.

## Run locally

```bash
pnpm infra:up                                  # from the repo root: Postgres, Kafka, Keycloak, ...
mvn -pl services/pricing-service -am spring-boot:run   # reads secrets from the root .env
```

## Test

```bash
mvn -pl services/pricing-service -am verify   # unit + Testcontainers integration tests + JaCoCo gate (≥70%)
```

Coverage report: `services/pricing-service/target/site/jacoco/index.html`.

## Metrics

Alongside the standard Spring Boot HTTP and JVM meters, the service publishes three
business meters on `/actuator/prometheus`. They answer "is pricing behaving", which the
platform dashboard cannot infer from request counts alone.

| Meter | Type | Tags | Why it exists |
|---|---|---|---|
| `pricing_quotes_issued_total` | counter | `service_level`, `size_class` | Demand, split the two ways the fee formula branches. Incremented only after the snapshot is stored, so it counts quotes a customer can actually use |
| `pricing_quote_fee_LKR` | summary (p50, p95) | `service_level` | What customers are really quoted. A pricing mistake that makes every quote too cheap shows here and nowhere else |
| `pricing_rate_card_selection_failures_total` | counter | `reason` | `NO_RATE_CARD` and `AMBIGUOUS_RATE_CARD` are configuration errors an admin must fix; a 4xx count alone would hide them among ordinary validation failures |

Tag cardinality is deliberately bounded: service level has two values, size class four, and
the reason is a problem code. No district, zone pair or quote id is ever a tag, because 81
lanes times a time series each is how a Prometheus instance falls over.
`MicrometerPricingMetricsTest` asserts that.

The meters reach Grafana through the existing scrape config (`pricing-service:8083` under
the `java-services` job) and are plotted by the provisioned dashboard
`infra/observability/grafana/dashboards/pricing-overview.json`, uid `swiftdrop-pricing`.

`PricingMetrics` is an outbound port in `application/port/out`, implemented by
`MicrometerPricingMetrics` in `infrastructure/metrics`, so the application layer stays free
of the metrics library exactly as it is free of JPA and Redis.

## Demonstrate the feature

`scripts/demo-m2-pricing.sh` exercises a running deployment and prints a pass or fail line
per behaviour: an anonymous estimate whose breakdown reconciles to the total, a longer lane
costing more, an admin rate change that closes the card in force and versions a new one,
rejection of an overlapping period, an already-issued quote keeping its fee after that
change, the fields parcel-service will validate, and the authorization and expiry answers.

```bash
./scripts/demo-m2-pricing.sh                  # against the kind deployment (default)

DEMO_BASE_URL=http://localhost:8080/api/v1 DEMO_HOST_HEADER= \
DEMO_AUTH_URL=http://localhost:8180 DEMO_AUTH_HOST= \
  ./scripts/demo-m2-pricing.sh                # against the compose stack
```

It is repeatable: the change date is derived from the existing rate-card history, so each
run closes whatever card is open and versions a new one after it. That accumulation is the
designed behaviour, since a rate card is never edited or deleted in place; the script
prints the resulting history and the command to restore the seeded prices.

Quote expiry is skipped by default to keep the run short. To see it for real, either wait
out the TTL with `DEMO_WAIT_FOR_EXPIRY=1`, or shorten the lifetime:

```bash
helm upgrade swiftdrop infra/helm/swiftdrop -n swiftdrop \
  -f infra/helm/swiftdrop/values-dev.yaml \
  --set-string pricing-service.config.PRICING_QUOTE_TTL=PT1M
```

The boundary itself is asserted exactly, against a controlled clock, in `QuoteExpiryIT`
rather than by waiting.

## Kubernetes

The service has a thin Helm chart at `infra/helm/charts/pricing-service`, which supplies
only its image, port, probe paths and environment; every template comes from the
`swiftdrop-service` library chart. It runs two replicas by default: a quote is a Redis
snapshot, so any replica can answer a lookup for a quote another replica issued.

See [infra/helm/README.md](../../infra/helm/README.md) for installing it on kind.
