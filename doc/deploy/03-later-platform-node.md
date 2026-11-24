# Later: platform node

Add this only after the two-node demo in [02-phase-checklist.md](02-phase-checklist.md) has succeeded, including the stop/start check. The application does not need this node.

## Instance

- [ ] Turn on the platform-node flag in Terraform (or launch it by hand the same way as the first two).
- [ ] Type `m7i-flex.large`, Ubuntu 24.04 amd64, 50 GB gp3. Loki and Prometheus write more than the app nodes do.
- [ ] Same VPC, same subnet, same availability zone as the other two.
- [ ] Its own Elastic IP, associated with the instance, so the address survives a stop. SSH uses that address. Grafana is reached through the apps node's Ingress, not through a public port on this node.
- [ ] Security group: the same node-to-node rules (TCP 6443 toward the apps server, UDP 8472, TCP 10250). No 80/443 on this node. No 8472 from the internet.

**Check:** the new Elastic IP stays the same across one stop and start, before you install anything on it.

## Join the cluster

The k3s server stays on the apps node. This machine is an agent.

- [ ] `curl -sfL https://get.k3s.io | K3S_URL=https://<apps-private-ip>:6443 K3S_TOKEN=<token> sh -s - agent --node-label role=platform`
- [ ] `kubectl get nodes` shows three `Ready` nodes, and this one has label `role=platform`.

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

- [ ] Install with the upstream Helm charts (kube-prometheus-stack or the separate Grafana charts, plus the Argo CD chart).
- [ ] Set `nodeSelector: { role: platform }` on those releases.
- [ ] Point Alloy at the in-cluster Loki and Tempo. No application code change.

**Check:** `http://grafana.swiftdrop.local` opens, a pricing quote shows a trace or a log line, and `kubectl -n argocd get pods` is ready. `kubectl -n swiftdrop get pods -o wide` still shows application pods on the apps node.

## Cost and when to stop

A 10-hour demo on the original two machines is about $2 at the $0.10075 hourly price. This third machine for the same 10 hours adds about $1. While it is stopped, its Elastic IP is about $3.65 per month and the 50 GB disk is about $4 per month.

Stop it with the other two at the end of the demo. Do not release the Elastic IP if you want the same address next time.
