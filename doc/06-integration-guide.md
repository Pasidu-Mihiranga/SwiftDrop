# Integrating your service with the Helm charts and with pricing

> **SwiftDrop docs:** [01 Architecture](01-architecture.md) · [02 Implementation Plan](02-implementation-plan.md) · [03 Team & Contribution](03-team-contribution.md) · [04 M2 Pricing & Zones](04-m2-pricing-zones-issue-15.md)

Written for the other milestone owners. Two things M2 built affect you, and neither
requires you to change how you work day to day:

- `infra/helm`, the Kubernetes deployment for the whole platform. Your service already has
  a chart; you turn it on.
- `pricing-service`, if your feature needs a fee or a quote.

Nothing here changes `docker-compose.yml`. `pnpm infra:up` and `pnpm stack:up` behave
exactly as before, and that remains the normal way to develop.

---

## Part 1: Deploying your service on Kubernetes

### What already exists for you

Every one of the twelve workloads has a chart in `infra/helm/charts/<service>/`. It is
three small files, and it is already written:

| File | What it holds |
|---|---|
| `Chart.yaml` | a dependency on the `swiftdrop-service` library chart |
| `templates/main.yaml` | one line: `{{ include "swiftdrop-service.all" . }}` |
| `values.yaml` | your image, port, probe paths and environment |

Everything else, the Deployment, Service, ConfigMap, the three probes and the optional
HPA, comes from the library chart. You do not write Kubernetes YAML.

### Turning your service on

Two edits.

**1. Enable it in the environment overlay.** In `infra/helm/swiftdrop/values-dev.yaml`,
find your service and flip the flag:

```yaml
user-service:
  enabled: true
```

**2. Add it to the image build list** in `scripts/kind-deploy.sh`:

```bash
services=("pricing-service" "api-gateway" "user-service")
```

Then `make kind-deploy`. That builds your image, loads it into the kind node, refreshes the
Secrets and runs `helm upgrade`.

### Adding or changing your environment variables

Put non-secret values in your own chart's `values.yaml` under `config:`:

```yaml
config:
  DB_NAME: users_db
  DB_USER: user_svc
  MY_FEATURE_FLAG: "true"
```

Values merge rather than replace, so you can add a key in `values.yaml` and override just
that key from the umbrella overlay without losing the rest. Shared settings (`DB_HOST`,
`REDIS_HOST`, `KEYCLOAK_ISSUER`, the Kafka bootstrap address) already arrive from
`global.commonConfig`; do not repeat them. `PORT` is derived from `containerPort`, so never
set it by hand.

**Anything secret goes in a Secret, never in a values file.** Add one line per key to
`scripts/kind-deploy.sh`:

```bash
add MY_API_KEY "${MY_API_KEY:-}"      # reads from the git-ignored .env
```

The three Secrets are `swiftdrop-app-secrets` (application credentials),
`swiftdrop-web-secrets` (the web app) and `swiftdrop-platform-secrets` (datastores and
Keycloak bootstrap). CI fails the build if a chart ever contains something shaped like a
credential, so this is enforced rather than a convention.

### If your service needs something that is not installed

The charts deliberately do not install operators. Your service is currently disabled for
one of these reasons, stated inline next to it in `values-dev.yaml`:

| You need | Status | What to do |
|---|---|---|
| **Kafka** (merchant, parcel, dispatch, reporting, tracking, notification, webhook) | not installed | Install the Strimzi operator and create a `Kafka` resource named `kafka` in the `swiftdrop` namespace. `global.commonConfig` already points at `kafka-kafka-bootstrap:9092`, so no chart change is needed. |
| **MongoDB** (tracking, notification, webhook) | **no template exists** | Copy `infra/helm/swiftdrop/templates/redis.yaml` (86 lines) to `mongodb.yaml` and change the image, port and probe. Put `MONGO_URI` in `swiftdrop-app-secrets`, because it contains credentials. |
| **RustFS / S3** (parcel proof-of-delivery) | **no template exists** | Same approach; `parcel-service` expects it at `http://object-storage:9000`. |
| **Grafana and the LGTM stack** | not installed | Install the Grafana Helm charts, then set `grafana.enabled: true` to publish the `grafana.swiftdrop.local` Ingress host. |
| **HPA actually scaling** | `metrics-server` not installed | `values-prod.yaml` enables three HPAs. Install `metrics-server` or they sit at unknown targets and never scale. |

These are listed so nobody discovers them at demo time. None of them is blocked by M2's
work; each belongs with the service that needs it.

### Adding a thirteenth service

```bash
mkdir -p infra/helm/charts/my-service/templates
# Chart.yaml       copy pricing-service's, change name and description
# templates/main.yaml   exactly: {{ include "swiftdrop-service.all" . }}
# values.yaml      image, containerPort, probe paths, config, secretRefs
# then add a dependency block with a condition to infra/helm/swiftdrop/Chart.yaml
./scripts/helm-deps.sh && make helm-lint
```

### The one thing that will catch you out

`helm dependency update` is not recursive, and each thin chart vendors the library chart as
a `.tgz`. **After editing the library chart, re-run `./scripts/helm-deps.sh`**, or
`helm template` keeps rendering the previous version and you will debug a change that was
never applied.

On Colima, or Docker Desktop with the containerd snapshotter, `kind load docker-image`
fails with `content digest not found`. `scripts/kind-deploy.sh` works around it by saving
the host platform explicitly; if you load an image by hand, do the same.

### Where to look when a pod will not start

| Symptom | Likely cause |
|---|---|
| `CreateContainerConfigError` | a Secret named in `.Values.secrets` does not exist. Run `kind-deploy.sh`, which creates all three. |
| `ErrImageNeverPull` | the image was not loaded into the node, or is missing from the `services` array in `kind-deploy.sh`. |
| Restart loop with probe failures, no application error | the pod is starving, not broken. The dev overlay already allows ten minutes for startup; a Spring Boot context can take three minutes when several JVMs share two cores. |
| Pods stuck during an upgrade | the dev overlay sets `maxSurge: 0` for this reason. Do not raise it on a small cluster. |

Useful commands:

```sh
kubectl -n swiftdrop get pods
kubectl -n swiftdrop logs deploy/<service> --tail=50
kubectl -n swiftdrop describe pod <pod>          # the Events section
kubectl -n swiftdrop get cm <service> -o yaml    # the environment it actually received
helm -n swiftdrop list
```

---

## Part 2: Using pricing from your service

### Getting a quote (any caller)

`POST /api/v1/pricing/quotes` is public and needs no token, because the public `/quote`
page uses it.

```bash
curl -X POST http://api.swiftdrop.local/api/v1/pricing/quotes \
  -H 'content-type: application/json' \
  -d '{"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,
       "sizeClass":"M","serviceLevel":"STANDARD","codAmount":0}'
```

It answers with a `quoteId`, the `fee`, a `breakdown` whose lines sum exactly to the fee,
the resolved zones, and `issuedAt` / `expiresAt`. The quote is valid for
`PRICING_QUOTE_TTL`, 15 minutes by default.

### Validating a quote (this is the M3 contract)

`GET /api/v1/pricing/quotes/{quoteId}` requires a bearer token and returns the stored
snapshot. Use it to check a `quoteId` a customer submitted.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://api.swiftdrop.local/api/v1/pricing/quotes/$QUOTE_ID
```

Four things worth knowing before you build against it:

1. **The response is the snapshot, verbatim.** Nothing is recalculated on lookup, and
   request data is never substituted into it. A quote keeps the fee it was given even
   after an admin changes the rate card.
2. **Unknown and expired both answer `404 QUOTE_NOT_FOUND`**, deliberately identical, so an
   expired id is not distinguishable from a guess. Do not treat 404 as "expired".
3. **Any valid token works**; no particular role is required.
4. Fields available to validate against your own request: `fee`, `breakdown`, `fromZone`,
   `toZone`, `fromDistrict`, `toDistrict`, `weightKg`, `sizeClass`, `serviceLevel`,
   `codAmount`, `issuedAt`, `expiresAt`.

`scripts/demo-m2-pricing.sh` performs exactly this call and asserts every field is
present, so you can see a worked example before writing code.

### Coverage, for a form or a validation

`GET /api/v1/pricing/zones` is public and lists the active zones with their districts. Use
it rather than hardcoding districts: the list is seed data in the pricing database, and no
district or fee literal may appear in Java or frontend code.

### Error codes you may see

| Status | `code` | Meaning |
|---|---|---|
| 422 | `SIZE_CLASS_WEIGHT_EXCEEDED` | the weight exceeds that size class's limit |
| 422 | `NO_RATE_CARD` | no rate card is in force for that lane; a configuration error |
| 422 | `DISTRICT_NOT_SERVICED` | the district is outside the active coverage |
| 409 | `RATE_CARD_OVERLAP` | admin only: the new card's period overlaps an existing one |
| 404 | `QUOTE_NOT_FOUND` | unknown or expired quote |
| 403 | `FORBIDDEN` | admin pricing endpoints need the ADMIN role |

All failures are problem+json with a stable `code`. Branch on `code`, not on the message.

### Admin pricing

Everything under `/api/v1/admin/pricing/**` is ADMIN-only and already has a UI at
`/admin/pricing`. A rate change never edits a row: it closes the card in force with
`PUT .../rate-cards/{id}/closure` and adds a new one with `POST .../rate-cards`. Periods
are half-open, so a card starting the day another ends leaves neither a gap nor an overlap.

---

## Who owns what, so edits do not collide

| File | Owner | Conflict risk |
|---|---|---|
| `infra/helm/swiftdrop-service/**` | M2 | none; nobody else should edit it |
| `infra/helm/charts/<your-service>/**` | you | none; separate files |
| `infra/helm/swiftdrop/values-dev.yaml` | shared | low: one block each, order-independent |
| `infra/helm/swiftdrop/Chart.yaml` | shared | only when adding a new service |
| `scripts/kind-deploy.sh` | shared | the `services=(...)` line is the one real hotspot |
| `contracts/openapi/pricing-service.v1.yaml` | M2 | coordinate before changing |

The realistic worst case is two people appending to `values-dev.yaml` and the `services`
array in the same week. Both are one-line conflicts.
