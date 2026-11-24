"""System figure S4: how SwiftDrop is built, shipped and deployed.

Layout rules:
  - three stacked bands: delivery pipeline (GitHub), the Kubernetes cluster, and the local alternative
  - inside the cluster the ingress sits on top, every workload drops straight down to its store row,
    so no line crosses another
  - images flow left to right along the pipeline and then down into the cluster at one point
"""

from draw import GREY, band, cylinder, label, node, path, svg, text

W, H = 2060, 1250
L, R = 60, 2000


def build() -> str:
    b = []
    # ------------------------------------------------------------ delivery pipeline
    b.append(band(L, 30, R - L, 190, "Build and release  ·  GitHub"))
    steps = [
        (90, "Pull request", ["feature branch to dev"]),
        (420, "CI workflows", ["ci-java · ci-node · ci-python", "ci-platform · helm · no-emoji"]),
        (790, "Kind end-to-end", ["helm-e2e.yml", "install + M2 demo script"]),
        (1160, "release.yml", ["build every image", "Trivy scan"]),
        (1530, "GHCR", ["ghcr.io/.../swiftdrop-*", "one tag per commit"]),
    ]
    for x, title, lines in steps:
        b.append(node(x, 80, 290, 104, title, lines, header=True))
    for (x1, *_), (x2, *_) in zip(steps, steps[1:]):
        b.append(path([(x1 + 290, 132), (x2, 132)]))

    # ------------------------------------------------------------ cluster
    b.append(band(L, 250, R - L, 760, "Kubernetes cluster  ·  Helm umbrella chart infra/helm/swiftdrop"))
    b.append(path([(1660, 184), (1660, 290)], dashed=True, end="open"))
    b.append(label(1670, 262, "pull images", size=11.5))

    # ingress
    b.append(node(380, 290, 1300, 74, "Ingress (NGINX)",
                  ["swiftdrop.local -> web  ·  api.swiftdrop.local -> api-gateway  ·  auth.swiftdrop.local -> keycloak"]))

    # edge workloads under the ingress
    edge = [(420, "web", ["Next.js 16 · BFF", "Deployment"]), (900, "api-gateway", ["Spring Cloud Gateway", "HPA 2-6 in prod"]),
            (1380, "keycloak", ["realm swiftdrop", "theme mounted"])]
    for x, title, lines in edge:
        b.append(node(x, 410, 260, 96, title, lines, header=True))
        b.append(path([(x + 130, 364), (x + 130, 410)]))

    # services row: one Deployment each, reached from the gateway
    services = ["user", "merchant", "pricing", "parcel", "dispatch", "reporting", "tracking", "notification", "webhook", "ai-assistant"]
    sx0, sw, pitch = 90, 168, 186
    b.append(path([(1030, 506), (1030, 548)], head=False))
    b.append(path([(sx0 + sw / 2, 548), (sx0 + 9 * pitch + sw / 2, 548)], head=False))
    for i, s in enumerate(services):
        x = sx0 + i * pitch
        b.append(node(x, 580, sw, 86, f"{s}-service" if s != "ai-assistant" else "ai-assistant", ["Deployment", "chart from the library"],
                      size=12))
        b.append(path([(x + sw / 2, 548), (x + sw / 2, 580)]))
    b.append(label(1046, 528, "routes /api/v1/**", size=11.5))

    # data row: StatefulSets
    stores = [(150, "PostgreSQL", ["one database per service"]), (470, "MongoDB", ["tracking · notifications", "webhooks"]),
              (790, "Redis", ["quotes · rate limits", "sockets · queues"]), (1110, "Kafka (KRaft)", ["7 topics", "topic Job per release"]),
              (1430, "RustFS", ["S3 proofs of delivery"]), (1750, "Mailpit", ["SMTP in dev"])]
    b.append(node(sx0, 692, 9 * pitch + sw, 40,
                  "cluster network  ·  each service reaches only its own stores  ·  StatefulSets in the dev and full profiles, "
                  "managed services in production", size=12))
    for i in range(10):
        x = sx0 + i * pitch + sw / 2
        b.append(path([(x, 666), (x, 692)]))
    for x, title, lines in stores:
        b.append(cylinder(x, 770, 220, 110, title, lines))
        b.append(path([(x + 110, 732), (x + 110, 770)]))

    # configuration strip
    b.append(node(150, 910, 820, 74, "Secrets and ConfigMaps",
                  ["platform secret (DB, Mongo, S3 keys) · per-service secrets · topic script ConfigMap"]))
    b.append(node(1110, 910, 860, 74, "Values overlays",
                  ["values.yaml · values-dev.yaml · values-full.yaml (every service) · values-prod.yaml"]))

    # ------------------------------------------------------------ local
    b.append(band(L, 1040, R - L, 180, "Local development  ·  docker compose"))
    for x, title, lines in [(90, "infra profile", ["Postgres, Mongo, Redis, Kafka", "Keycloak, RustFS, Mailpit"]),
                            (560, "apps profile", ["every service + web", "built from the repo root"]),
                            (1030, "observability profile", ["Alloy, Loki, Tempo", "Prometheus, Grafana"]),
                            (1500, "kind-deploy.sh", ["SWIFTDROP_PROFILE=dev|full", "same images, Helm install"])]:
        b.append(node(x, 1090, 400, 96, title, lines, header=True))
    b.append(text(R - 20, 1205, "Grafana dashboards are provisioned from infra/observability; in Kubernetes the official Grafana charts are used",
                  11, GREY, anchor="end"))
    return svg(W, H, "".join(b), "Deployment: build, release and run")
