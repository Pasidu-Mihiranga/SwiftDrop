# M7 monitoring: merchants and the merchant API

The Grafana dashboard **SwiftDrop – Merchants & Merchant API (M7)** (uid `swiftdrop-merchants`,
file `infra/observability/grafana/dashboards/merchant-overview.json`) shows merchant onboarding in
merchant-service and the merchant order API in parcel-service. The platform dashboard answers
whether the services are healthy; this one answers whether onboarding and merchant ordering are
behaving.

## Running it locally

The dashboard is provisioned like the others. Grafana loads every JSON file in
`infra/observability/grafana/dashboards` into the **SwiftDrop** folder, and Prometheus already
scrapes merchant-service (8082) and parcel-service (8084) under the `java-services` job. Nothing
else needs configuring.

```bash
docker compose --profile infra --profile observability up -d
```

Open Grafana at <http://localhost:3001>, using `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`
from `.env`. Then go to **Dashboards → SwiftDrop → SwiftDrop – Merchants & Merchant API (M7)**.

The compose Prometheus scrapes the services by their container names (`merchant-service:8082`).
When the services run on your machine with `java -jar` instead of in compose, Prometheus cannot
reach them and the panels stay empty. Either run the services in compose too, or point a
Prometheus at `host.docker.internal:8082` and `:8084`.

## Metrics

Each metric below was added for M7 following the existing pattern: a port in `application.port.out`
and a Micrometer adapter in `infrastructure.metrics`, as in pricing-service's
`MicrometerPricingMetrics`. Tags are closed lists and never a merchant id, so the number of series
does not grow with the number of merchants. Counters are registered at 0 when the service starts,
so Prometheus' `increase()` also sees the first event.

| Prometheus name | Type | Tags | Service | Counted when |
|---|---|---|---|---|
| `merchant_status_changes_total` | counter | `from` (`NONE`, `PENDING`, `APPROVED`), `to` (`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`) | merchant-service | After the change committed. `from="NONE"` is a new application (also a re-application after a rejection). |
| `merchant_applications` | gauge | `status` | merchant-service | Read from the `merchant` table with one grouped query, at most every 30 s whatever the scrape rate. If the read fails the value is NaN (a gap in Grafana), never a made-up 0. |
| `merchant_credential_rotations_total` | counter | none | merchant-service | After Keycloak issued the new secret and the record committed. |
| `parcel_merchant_orders_total` | counter | `outcome` | parcel-service | Once per request that reaches the order logic (`MerchantOrderService.create`), with how it ended. A new order only after its transaction committed. |

`outcome` values for `parcel_merchant_orders_total`:

| Outcome | HTTP | Meaning |
|---|---|---|
| `created` | 201 | A new parcel was stored |
| `replayed` | 200 | The same Idempotency-Key and body as before: the original parcel was returned (also when the request lost a race to an identical one) |
| `idempotency_conflict` | 409 | The key was already used for a different order (`IDEMPOTENCY_KEY_REUSED`) |
| `idempotency_in_progress` | 409 | A concurrent request with the same key had not finished (`IDEMPOTENCY_KEY_IN_PROGRESS`) |
| `not_approved` | 403 | The merchant is not APPROVED, or parcel-service has not seen it approved |
| `no_merchant` | 403 | The token names no merchant |
| `not_priceable` | 422 | pricing-service refused the order |
| `pricing_unavailable` | 503 | pricing-service unreachable or its circuit breaker open |
| `failed` | 5xx | Anything else, such as a database error |

Requests refused **before** the order logic are not order outcomes: a missing or malformed
Idempotency-Key or body (400), or a missing token or wrong role (401/403). The HTTP status panel
shows those, from Spring Boot's standard `http_server_requests_seconds` timer.

The dashboard also uses existing metrics:
- `up`;
- `http_server_requests_seconds_count`, `_sum` and `_max` (labels `service`, `uri`, `method`, `status`);
- kafka-exporter's `kafka_consumergroup_lag`.

## Panels

**Merchant onboarding (merchant-service)**

| Panel | Query (summary) |
|---|---|
| New applications / Approvals / Rejections / Suspensions (selected range) | `sum(increase(merchant_status_changes_total{to="…"}[$__range]))` |
| Applications by status (now) | `max by (status) (merchant_applications)` |
| Onboarding activity (per hour) | `sum by (to) (increase(merchant_status_changes_total[1h]))` |
| API credential rotations (selected range) | `sum(increase(merchant_credential_rotations_total[$__range]))` |

**Merchant order API (parcel-service)**

| Panel | Query (summary) |
|---|---|
| Orders created / Idempotent replays / Idempotency conflicts (selected range) | `sum(increase(parcel_merchant_orders_total{outcome=~"…"}[$__range]))` |
| Successful order requests (selected range) | created + replayed, divided by all order requests; no value (not 0%) when there were none |
| Order requests by outcome (per min) | `sum by (outcome) (rate(parcel_merchant_orders_total[5m])) * 60` |
| POST /api/v1/merchant/orders responses by HTTP status (per min) | `http_server_requests_seconds_count{service="parcel-service", uri="/api/v1/merchant/orders", method="POST"}` by `status` |
| Refused order requests by reason (per min) | outcomes other than created and replayed |
| Idempotency: replays and conflicts (per min) | `replayed` against `idempotency_conflict` + `idempotency_in_progress` |

**Health**

| Panel | Query (summary) |
|---|---|
| merchant-service up / parcel-service up | `max(up{service="…"})` |
| M7 endpoints 5xx ratio (5m) | 5xx share of `/api/v1/merchants…`, `/api/v1/admin/merchants…` and `/api/v1/merchant/orders`; 0 when nothing failed, no value when there was no traffic |
| Merchant lifecycle consumer lag | `sum(kafka_consumergroup_lag{consumergroup="parcel-service", topic="swiftdrop.merchant.lifecycle.v1"} >= 0)`; kafka-exporter reports -1 for a partition with no committed offset yet, and those are left out rather than summed |
| Pricing unavailable for orders (selected range) | `outcome="pricing_unavailable"` |
| Average response time by endpoint | `rate(…_sum) / rate(…_count)` per endpoint |
| Slowest request in the last scrape | `http_server_requests_seconds_max` per endpoint |

## How it was verified

- Unit tests for both Micrometer adapters and the counting in the services.
- Integration tests with real Postgres, checking the counts and the counters.
- A live check: a scratch Prometheus scraped merchant-service and parcel-service running on the
  host, plus kafka-exporter. Grafana 12.4.13 loaded this repository's own provisioning and
  dashboards folders read-only. Onboarding and order traffic went through the gateway:
  - 3 applications; 1 approved and then suspended, 1 rejected
  - 1 credential rotation
  - 1 new order, a replay, a key conflict, an unapproved merchant, and a request with no key
- Results:
  - Grafana provisioned this dashboard in the SwiftDrop folder next to the existing two.
  - Every panel query ran without errors.
  - The values matched the traffic exactly.

## Known limits

- **No latency percentiles.** The services do not publish HTTP latency histograms, so the
  dashboard shows mean and maximum latency, not p95.
- **The status gauge counts application rows.** A rejected application is kept as history, so
  an owner who applied again appears once as REJECTED and once in their current status.
- **Counters reset when a service restarts.** `increase()` and `rate()` handle that, but a
  count over a range that includes a restart can be slightly low.
- **Consumer lag needs kafka-exporter**, which runs only under the `observability` profile.
- **No outbox panel.** The shared outbox library publishes no metrics, so there is no panel for
  events waiting to be published.
