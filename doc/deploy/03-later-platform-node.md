# Later: platform node

Add this only after the two-node demo in [02-phase-checklist.md](02-phase-checklist.md) has succeeded, including the stop/start check. The application does not need this node.

## Instance

- [x] Turn on the platform-node flag in Terraform (or launch it by hand the same way as the first two).
- [ ] Type `m7i-flex.large`, Ubuntu 24.04 amd64, 50 GB gp3. Loki and Prometheus write more than the app nodes do.
- [ ] Same VPC, same subnet, same availability zone as the other two.
- [ ] Its own Elastic IP, associated with the instance, so the address survives a stop. SSH uses that address. Grafana is reached through the apps node's Ingress, not through a public port on this node.
- [ ] Security group: the same node-to-node rules (TCP 6443 toward the apps server, UDP 8472, TCP 10250). No 80/443 on this node. No 8472 from the internet.

**Check:** the new Elastic IP stays the same across one stop and start, before you install anything on it. Done: `52.63.231.37` stayed after one stop and start. Private address `10.20.1.173`. Instance `i-025e572c9e3b8327d`.

## Join the cluster

The k3s server stays on the apps node. This machine is an agent.

- [x] `curl -sfL https://get.k3s.io | K3S_URL=https://<apps-private-ip>:6443 K3S_TOKEN=<token> sh -s - agent --node-label role=platform`
- [x] `kubectl get nodes` shows three `Ready` nodes, and this one has label `role=platform`.

## What to install here

Only platform pods. Do not schedule SwiftDrop services or databases onto this node.

| Tool | Role |
|---|---|
| Prometheus | Scrapes the services. The chart already exposes `/actuator/prometheus` and `/metrics`. |
| Grafana | Dashboards already live in `infra/observability/grafana`. |
| Alloy | Collects logs and OpenTelemetry from the nodes. |
| Loki | Stores logs. Keep retention short (a day is enough for a demo). |
| Tempo | Stores traces. The services already emit OpenTelemetry. |
| Argo CD | Syncs `infra/helm/swiftdrop`. It does not replace the first Helm install; it takes over once that install is healthy. |

`grafana.enabled` in the umbrella values only publishes the Ingress host `grafana.swiftdrop.local`. Install Grafana first, then set `grafana.enabled: true` so the apps node's Ingress points at the Grafana Service.

- [x] Install with the upstream Helm charts (kube-prometheus-stack or the separate Grafana charts, plus the Argo CD chart).
- [x] Set `nodeSelector: { role: platform }` on those releases. Alloy is the exception: one small collector runs on each node, because that is where the logs are written. Loki, Tempo, Prometheus, Grafana, and Argo CD stay on `role=platform`.
- [x] Point Alloy at the in-cluster Loki and Tempo. No application code change. Pricing sends its traces to Alloy. The other services do not, yet.

**Check:** Grafana answers inside the cluster, Loki has service logs, and Tempo has traces from pricing-service. Argo CD pods are ready. Application pods are still on the apps node and databases on the data node. The public Grafana address stays off until `grafana.swiftdrop.sapunipathirana.me` has an A record for `52.62.242.130`, so the existing certificate is not reissued early.

## Cost and when to stop

A 10-hour demo on the original two machines is about $2 at the $0.10075 hourly price. This third machine for the same 10 hours adds about $1. While it is stopped, its Elastic IP is about $3.65 per month and the 50 GB disk is about $4 per month.

Stop it with the other two at the end of the demo. Do not release the Elastic IP if you want the same address next time.
