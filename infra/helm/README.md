# Kubernetes / Helm (Phase 4 · owner M2)

Planned layout (see doc/02-implementation-plan.md §10):

```
helm/
├── swiftdrop/            umbrella chart — values-dev.yaml (kind/minikube), values-prod.yaml
├── swiftdrop-service/    library chart — Deployment, Service, ConfigMap, Secret, HPA, probes (DRY)
└── charts/<service>/     thin per-service charts using the library chart
```

Every service already follows the conventions the chart relies on: configuration only through environment
variables, `/actuator/health/{liveness,readiness}` (Java) or `/health/live` (Node/Python), metrics at
`/actuator/prometheus` or `/metrics`, JSON logs on stdout, non-root containers.
