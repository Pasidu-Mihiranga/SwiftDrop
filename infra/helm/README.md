# Kubernetes / Helm (owner M2)

SwiftDrop on Kubernetes: a library chart supplies the templates, a thin chart per workload
supplies that workload's values, and an umbrella chart composes them with the dev
datastores and the Ingress.

```
infra/helm/
├── swiftdrop-service/        library chart: Deployment, Service, ConfigMap, probes, HPA
├── charts/<service>/         thin chart per workload (12 of them), values only
├── swiftdrop/                umbrella chart: datastores, Ingress, values-dev, values-prod
└── kind-cluster.yaml         kind node config with 80/443 published
```

## How the three layers fit together

`swiftdrop-service` is a Helm library chart, so it renders nothing by itself. Each thin
chart declares it as a dependency and contains a single template whose entire body is:

```gotemplate
{{ include "swiftdrop-service.all" . }}
```

Everything else about a workload is values. A thin chart therefore only states what makes
it different: its image, its port, its probe paths and its own environment. Adding a shared
capability (a PodDisruptionBudget, a ServiceAccount, a sidecar) means editing the library
once instead of twelve charts.

A library chart's `values.yaml` is **not** merged into charts that depend on it, because
Helm namespaces a dependency's values under its chart name. The defaults therefore live in
`swiftdrop-service/templates/_defaults.tpl`, and `swiftdrop-service.values` layers them in
this order, last one winning:

1. the library defaults,
2. `global.defaults` from the umbrella (change a probe timeout for every service at once),
3. the thin chart's own values, including anything the umbrella or `-f` overrides.

Environment shared by every service (`DB_HOST`, `KEYCLOAK_ISSUER`, `REDIS_HOST`, the Kafka
bootstrap address) comes from `global.commonConfig` and is rendered into each service's
ConfigMap. `PORT` is derived from `containerPort`, so the application and its probes cannot
disagree about which port it listens on.

## Conventions the charts rely on

Every service already satisfies these, which is what makes one library chart enough:

| Concern | Java (Spring Boot) | Node (NestJS) | Python (FastAPI) | Web (Next.js) |
|---|---|---|---|---|
| Liveness | `/actuator/health/liveness` | `/health/live` | `/health/live` | `/` |
| Readiness | `/actuator/health/readiness` | `/health/live` | `/health/live` | `/` |
| Metrics | `/actuator/prometheus` | `/metrics` | `/metrics` | none |
| Config | environment variables only | same | same | same |
| User | uid 10001 | uid 1000 (`node`) | uid 10001 | uid 1000 |

The Node and Python services serve `/health/live` and `/metrics` from the shared libraries
(`libs/node/swiftdrop-common`, `libs/python/swiftdrop_common`). Neither has a separate
readiness endpoint yet, so readiness reuses the liveness path; when one is added, only the
thin chart's `probes.readiness.path` changes.

## Secrets

No chart contains a credential, and no values file does either. Three Secrets are created
before installing, and the charts reference them by name only:

| Secret | Used by | Holds |
|---|---|---|
| `swiftdrop-platform-secrets` | postgres, redis, keycloak | `POSTGRES_PASSWORD`, `SWIFTDROP_DB_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `REDIS_PASSWORD`, `KC_BOOTSTRAP_ADMIN_*`, the `KC_*_CLIENT_SECRET` placeholders the realm file resolves, `KC_SEED_USER_PASSWORD`, Google IdP credentials |
| `swiftdrop-app-secrets` | the services | `DB_PASSWORD`, `REDIS_PASSWORD`, `PII_ENCRYPTION_KEY`, S3 keys, LLM keys |
| `swiftdrop-web-secrets` | web | `AUTH_SECRET`, `AUTH_KEYCLOAK_SECRET` |

`scripts/kind-deploy.sh` creates all three from the git-ignored `.env`, so the same
generated credentials back compose and the cluster. In production they come from the
cluster's secret manager or a CI-created Secret; `values-prod.yaml` only names them.

## Prerequisites

| Tool | Notes |
|---|---|
| Docker | The repository's compose stack already requires it. On macOS with Colima, the scripts export `DOCKER_HOST` automatically. |
| kind 0.20+ | Or minikube with the `ingress` addon, in which case create the cluster yourself and skip `kind-up.sh`. |
| kubectl 1.29+ | |
| Helm 3.14+ | Helm 4 works as well. |

Two things the architecture needs but this chart deliberately does not install, because
they are operators with their own lifecycle:

- **Kafka** through the [Strimzi](https://strimzi.io) operator. `global.commonConfig`
  already points at the bootstrap Service a default Strimzi cluster named `kafka` creates
  (`kafka-kafka-bootstrap:9092`). Every Kafka consumer service is disabled in
  `values-dev.yaml` until Strimzi is installed.
- **RustFS** (S3-compatible storage for proof-of-delivery photos), needed by
  parcel-service. `parcel-service` expects it at `http://object-storage:9000`.

OSRM, the routing engine behind same-day pricing, is templated but optional. Set
`osrm.enabled=true` and `pricing-service.config.OSRM_URL=http://osrm:5000`, as
`values-prod.yaml` does. Its first start downloads the Sri Lanka extract and builds the
graph in init containers, which takes about 2 minutes, 1.3 GB of memory and 1 GB of disk.
It stays off in dev to keep a kind cluster small. pricing-service does not wait for it:
until it answers, and whenever it fails, distances are estimated and labelled `ESTIMATED`.

MongoDB is likewise not templated here; the three Nest services need it and stay disabled
in dev until it is provided.

## Install on kind

```sh
# 1. Cluster plus the Ingress controller
make kind-up            # or ./scripts/kind-up.sh

# 2. Resolve the Ingress hosts (once per machine)
sudo sh -c 'echo "127.0.0.1 swiftdrop.local api.swiftdrop.local auth.swiftdrop.local grafana.swiftdrop.local" >> /etc/hosts'

# 3. Build the images, load them into the node, create the Secrets, install the chart
make kind-deploy        # or ./scripts/kind-deploy.sh
```

`kind-deploy.sh` is idempotent: re-run it after a code change and it rebuilds, reloads and
runs `helm upgrade`. Credentials are read from `.env` and passed to `kubectl` without being
printed.

One environment detail worth knowing: with the containerd image store (Colima, or Docker
Desktop with the containerd snapshotter) `kind load docker-image` fails with
`content digest ... not found`, because `docker save` writes a multi-platform archive that
kind cannot import. The script works around it by saving the host platform explicitly and
loading the archive, and falls back to the direct load where that is not supported.

What the dev install runs: `web`, `api-gateway` and `pricing-service`, plus Postgres, Redis
and Keycloak. The remaining nine services are present as charts but disabled, each with the
reason stated inline in `swiftdrop/values-dev.yaml` (Kafka, MongoDB, S3 or an LLM key).
Enable one by adding it to `values-dev.yaml` and to the `services` array in
`scripts/kind-deploy.sh` so its image is built and loaded.

### Verify

```sh
kubectl -n swiftdrop get pods
kubectl -n swiftdrop logs deploy/pricing-service | grep -i flyway

# Anonymous fee estimate through the gateway
curl -s -X POST http://api.swiftdrop.local/api/v1/pricing/quotes \
  -H 'content-type: application/json' \
  -d '{"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,"sizeClass":"M","serviceLevel":"STANDARD"}'

# Coverage list and metrics
curl -s http://api.swiftdrop.local/api/v1/pricing/zones
curl -s http://api.swiftdrop.local/actuator/prometheus | grep pricing_

# The public quote page
open http://swiftdrop.local/quote
```

If port 80 on the host is taken, drop `extraPortMappings` from `kind-cluster.yaml` and
reach the Ingress with a port-forward instead:

```sh
kubectl -n ingress-nginx port-forward svc/ingress-nginx-controller 8080:80
curl -s -H 'Host: api.swiftdrop.local' http://127.0.0.1:8080/api/v1/pricing/zones
```

### Tear down

```sh
make kind-down          # deletes the whole cluster
# or keep the cluster and remove the release only:
helm -n swiftdrop uninstall swiftdrop
```

PersistentVolumeClaims survive `helm uninstall`. Delete them with
`kubectl -n swiftdrop delete pvc --all` to start from an empty database, which is what
makes the Postgres init script run again.

## Production values

`values-prod.yaml` differs from dev in the ways that matter rather than in scale alone:

- images come from `global.imageRegistry` by immutable tag, never `latest`;
- TLS is terminated at the Ingress with a cert-manager issuer, and
  `nginx.ingress.kubernetes.io/ssl-redirect` is on;
- `postgres`, `redis` and `keycloak` are **off**, because production points at managed
  instances through `global.commonConfig`;
- HPAs are enabled on api-gateway, parcel-service and tracking-service;
- the web app's `AUTH_URL` is https. This matters: `apps/web/src/lib/gateway.ts` derives the
  session cookie's `secure` flag from that URL's scheme, so `AUTH_URL` and the Ingress hosts
  must always agree.

## Working on the charts

```sh
make helm-lint          # vendors dependencies, lints, and renders both overlays
./scripts/helm-deps.sh  # after editing the library chart, before lint/template/install
```

`helm dependency update` is not recursive, and each thin chart vendors the library as a
`.tgz` under its own `charts/`. Those archives are git-ignored and regenerated by
`scripts/helm-deps.sh`; `Chart.lock` is committed. **After editing the library chart you
must re-run the script**, or `helm template` will keep rendering the previous version.

`.github/workflows/helm.yml` lints every chart, renders both overlays, checks the rendered
objects are well-formed, and fails if a chart ever contains something that looks like a
literal credential.
