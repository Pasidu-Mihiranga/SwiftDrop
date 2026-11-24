# Two-node k3s architecture

This is the demo layout for SwiftDrop on AWS. It uses two EC2 instances, k3s, and the Helm chart in `infra/helm`. A third instance, for Grafana, Loki, Tempo, and Argo CD, is described in [03-later-platform-node.md](03-later-platform-node.md). The click-by-click order is [02-phase-checklist.md](02-phase-checklist.md).

`main` at `011e04e` (`release: dev to main, every milestone complete (M1 to M10)`) already templates the broker and the extra datastores. `infra/helm/swiftdrop/values-full.yaml` turns on Kafka, MongoDB, RustFS, Mailpit, and every service except the AI assistant. The dev overlay still starts with only web, the gateway, and pricing.

## Instances

Both machines are the same size. Launch them in `ap-south-1`, in one availability zone (for example `ap-south-1a`), in one public subnet. Do not create a NAT gateway. Do not use EKS.

| Node | Type | Disk | k3s | What runs there |
|---|---|---|---|---|
| Apps | `m7i-flex.large` (2 vCPU, 8 GiB), Ubuntu 24.04 amd64 | 40 GB gp3 | server | web, all twelve services, ingress-nginx |
| Data | `m7i-flex.large`, same AMI | 40 GB gp3 | agent | Postgres, Redis, Keycloak, Kafka, MongoDB, RustFS, Mailpit |

`m7i-flex.large` is a flex instance: AWS documents a 40% CPU baseline and full CPU for most of a day. One person walking through a warm demo should see quotes and pages in well under a second. All twelve application processes share the apps node's 2 vCPUs, so a load test will queue. Start the instances about twenty minutes before a demo and open the quote page once.

OSRM stays off. Pricing estimates the distance when `OSRM_URL` is blank. The production overlay (`values-prod.yaml`) asks for two replicas and a gateway autoscaler. Leave that overlay unused on these two machines.

## Elastic IPs

Each instance gets its own Elastic IP, associated with the instance.

An auto-assigned public address is released when the instance stops, and a new one appears on the next start. An Elastic IP stays on the instance across stop and start. That is the address you put in your laptop's hosts file and in the Keycloak issuer.

- The apps Elastic IP is the public address for `swiftdrop.local`, `api.swiftdrop.local`, and `auth.swiftdrop.local`. `values-dev.yaml` sets `AUTH_URL` to `http://swiftdrop.local`. The browser and Keycloak must keep using that name, resolved to this Elastic IP.
- The data Elastic IP keeps SSH to the data node stable. Pods still reach Postgres, Kafka, and MongoDB on private IPs. Private IPs also survive a stop.

While an instance is stopped, its Elastic IP remains allocated. Budget about $3.65 per month for each one. Two stopped nodes are about $7.30 per month for the addresses, plus the gp3 disks (40 GB is about $3.20 per disk per month).

## Network

k3s needs these ports, and only between the two security groups. The list is from the [k3s requirements](https://docs.k3s.io/installation/requirements).

- TCP 6443 on the apps node, from the data node. Agents reach the API here.
- UDP 8472 both ways. Flannel VXLAN. Never open this to the internet.
- TCP 10250 both ways. Kubelet metrics.

Also:

- TCP 22 from your own IP only, on both nodes.
- TCP 80 and 443 on the apps node, from the internet. ingress-nginx listens here.
- Postgres 5432, Redis 6379, MongoDB 27017, Kafka 9092, RustFS 9000, and Keycloak 8080 stay off the internet. Pods use the cluster network.

Outbound traffic is allowed so the nodes can download packages and images.

## What satisfies the Kubernetes mark

k3s is Kubernetes. The chart already renders the objects the brief asks for.

| Asked for | Where |
|---|---|
| Frontend | `web` Deployment and Service |
| Backend services | one Deployment and Service per service, from `infra/helm/swiftdrop-service` |
| Database | Postgres StatefulSet, Service, and ConfigMap in `templates/postgres.yaml` |
| Broker | Kafka Service and StatefulSet in `templates/kafka.yaml`, turned on by `values-full.yaml` |
| ConfigMaps | service chart `_configmap.tpl`, Postgres init, Keycloak realm, Kafka topics |
| Secrets | created with `kubectl` before install. Names are in `infra/helm/README.md`. No secret belongs in git |
| Ingress | `templates/ingress.yaml`, `className: nginx` |
| Helm | `infra/helm/swiftdrop` |

Disable Traefik when you install the k3s server (`--disable=traefik`) and install ingress-nginx. The chart's Ingress is written for nginx, not for the Traefik that k3s bundles.

## Scheduling on two nodes

Label the nodes `role=apps` and `role=data`.

The service library chart renders `nodeSelector` (`infra/helm/swiftdrop-service/templates/_deployment.tpl`). A values overlay can send web, the gateway, and the other services to `role=apps`.

The datastore templates do not render `nodeSelector`. Postgres, Redis, Keycloak, Kafka, MongoDB, RustFS, and Mailpit can land on either node until those templates grow a selector. On the first install, watch `kubectl get pods -o wide`. If the apps node starts evicting pods, add the selector to those templates before the demo. Do not turn on `values-prod.yaml` autoscaling to "make room".

Pricing stays at 2 replicas. `infra/helm/charts/pricing-service/values.yaml` sets that because a quote lives in Redis, so either pod can answer. That pair is the load-balancing demonstration. Every other service stays at 1 replica.

## What a warm demo feels like

After the twenty-minute warmup, a quote or a page for one person should return in well under a second. Login takes about one to two seconds because the browser walks through Keycloak. The first request after a pod starts can take a few seconds. That wait belongs before the audience arrives.
