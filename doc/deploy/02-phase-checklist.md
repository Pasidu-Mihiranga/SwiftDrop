# Deployment checklist

Do the phases in order. Each one ends with a check. Do not start the next phase until that check passes.

The chart this follows is `infra/helm/swiftdrop` on current `main`. Dev install: web, api-gateway, pricing, Postgres, Redis, Keycloak (`values-dev.yaml`). Full install: that, plus Kafka, MongoDB, RustFS, Mailpit, and the other services except the AI assistant (`values-full.yaml`). Details of the two machines are in [01-two-node-architecture.md](01-two-node-architecture.md).

Terraform for the two machines is in `infra/terraform`. Use Elastic IPs, not the auto-assigned public address.

The addresses below belong to the suspended Mumbai account. They do not exist in the new account. After the Sydney apply, replace them with the new Elastic IPs.

The old demo used these addresses:

- apps `i-086968a678385696b` — `3.111.117.223` (private `10.20.1.62`)
- data `i-0b7aa17aa88fcdd93` — `3.109.169.81` (private `10.20.1.160`)

## Phase 1 — Account and IAM

The IAM user is the person who runs Terraform from a laptop. It is not a role on the EC2 instances. Do not run Terraform as the account root. The instances do not need an instance role for this demo.

- [ ] In Billing, add alerts at $50 and $150 of credits. Left for later; not required to launch.
- [ ] Create an IAM user with access keys for the AWS CLI, and attach only the policy below. The existing `developer` user already applied this Terraform, so the sample policy was not attached.
- [ ] Confirm the new account can launch two `m7i-flex.large` (4 vCPU) in `ap-southeast-2`. The checked launch below was the suspended Mumbai account.

Paste this policy as `SwiftDropTerraform`. It allows every EC2 action Terraform uses in Sydney, including creating and attaching the two Elastic IPs. It also allows this user to open Billing and create budget alerts. A launch of any size other than `m7i-flex.large` is denied. EC2 calls outside `ap-southeast-2` are denied.

Before Billing will open for this user, sign in with the Google account, open **Billing**, then **Billing preferences**, and turn on **IAM user/role access to Billing information**.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Ec2InSydney",
      "Effect": "Allow",
      "Action": "ec2:*",
      "Resource": "*"
    },
    {
      "Sid": "ElasticIp",
      "Effect": "Allow",
      "Action": [
        "ec2:AllocateAddress",
        "ec2:ReleaseAddress",
        "ec2:AssociateAddress",
        "ec2:DisassociateAddress",
        "ec2:DescribeAddresses",
        "ec2:DescribeAddressesAttribute",
        "ec2:CreateTags",
        "ec2:DeleteTags"
      ],
      "Resource": "*"
    },
    {
      "Sid": "BillingAndBudgets",
      "Effect": "Allow",
      "Action": [
        "aws-portal:ViewBilling",
        "aws-portal:ViewUsage",
        "aws-portal:ViewAccount",
        "aws-portal:ViewPaymentMethods",
        "billing:Get*",
        "billing:List*",
        "budgets:ViewBudget",
        "budgets:ModifyBudget",
        "budgets:Describe*",
        "budgets:List*",
        "ce:Get*",
        "ce:List*",
        "ce:Describe*",
        "account:GetAccountInformation",
        "invoicing:GetInvoicePDF",
        "invoicing:ListInvoiceSummaries",
        "freetier:GetFreeTierUsage",
        "payments:ListPaymentPreferences"
      ],
      "Resource": "*"
    },
    {
      "Sid": "DenyEc2OutsideSydney",
      "Effect": "Deny",
      "Action": "ec2:*",
      "Resource": "*",
      "Condition": {
        "StringNotEquals": { "aws:RequestedRegion": "ap-southeast-2" }
      }
    },
    {
      "Sid": "DenyOtherInstanceSizes",
      "Effect": "Deny",
      "Action": "ec2:RunInstances",
      "Resource": "arn:aws:ec2:ap-southeast-2:*:instance/*",
      "Condition": {
        "StringNotEquals": { "ec2:InstanceType": "m7i-flex.large" }
      }
    }
  ]
}
```

Do not attach `AdministratorAccess`. Do not add `iam:PassRole` until an instance role is actually required (for example ECR).

**Check:** the user can call `aws ec2 describe-vpcs --region ap-southeast-2 --profile swiftdrop`, and a dry-run launch of any other instance type is denied.

## Phase 2 — Network, instances, Elastic IPs

The Terraform for this phase is in `infra/terraform`. Read `infra/terraform/README.md`, set your IP in `terraform.tfvars`, then `terraform plan` and `terraform apply`. It creates:

- [x] One VPC, one public subnet in a single AZ, one internet gateway, a route to `0.0.0.0/0`.
- [x] Two security groups, with the ports in [01-two-node-architecture.md](01-two-node-architecture.md).
- [x] Two `m7i-flex.large` instances, Ubuntu 24.04 amd64, 40 GB gp3 root volume.
- [x] Two Elastic IPs. Associate one with the apps instance and one with the data instance.
- [x] A variable or flag, default off, for the third platform instance. Leave it off.

**Check:** `aws ec2 describe-addresses` shows both Elastic IPs with an `InstanceId`. Write the two public addresses down. Stop the apps instance, start it, and confirm the public address is unchanged. Start the data instance again if you stopped it too.

## Phase 3 — k3s

On the apps node:

- [x] Install k3s as the server, with Traefik disabled: `curl -sfL https://get.k3s.io | sh -s - server --disable=traefik --node-label role=apps`
- [ ] Copy `/etc/rancher/k3s/k3s.yaml` to your laptop. Replace `127.0.0.1` with the apps private IP. Not required: port 6443 is only open to the data node, so use `sudo k3s kubectl` over SSH.

On the data node:

- [x] Join as an agent, using the apps private IP and the node token from the server: `curl -sfL https://get.k3s.io | K3S_URL=https://<apps-private-ip>:6443 K3S_TOKEN=<token> sh -s - agent --node-label role=data`

**Check:** `kubectl get nodes` shows both nodes `Ready`.

## Phase 4 — Ingress

- [x] Install ingress-nginx so it is the only ingress class and it binds 80 and 443 on the apps node.
- [ ] On your laptop, point the chart hostnames at the apps Elastic IP. The checks below used the Elastic IP with a `Host` header, so the hosts file is optional:

```text
<apps-elastic-ip>  swiftdrop.local api.swiftdrop.local auth.swiftdrop.local grafana.swiftdrop.local
```

**Check:** `curl -H 'Host: swiftdrop.local' http://<apps-elastic-ip>/` reaches the ingress (a 404 from nginx is enough; the app is not installed yet).

## Phase 5 — Dev Helm slice

This is the slice `values-dev.yaml` enables: web, api-gateway, pricing (2 replicas), Postgres, Redis, Keycloak.

- [x] On a machine with this repo: `pnpm env:init` if `.env` does not exist yet.
- [x] Build the web, gateway, and pricing images and import them into the apps node. `values-dev.yaml` sets `imagePullPolicy: Never`, so a node without the image cannot start the pod. App pods are pinned to the apps node, so the data node does not need these images. Pull the Postgres, Redis, and Keycloak images into the data node with `k3s ctr images pull`.
- [x] Create namespace `swiftdrop`.
- [x] Create the realm ConfigMap and the three Secrets the same way `scripts/kind-deploy.sh` does: `swiftdrop-realm` from `infra/keycloak/realm-swiftdrop.json`, then `swiftdrop-platform-secrets`, `swiftdrop-app-secrets`, and `swiftdrop-web-secrets`.
- [x] `./scripts/helm-deps.sh`
- [x] Install:

```sh
helm upgrade --install swiftdrop infra/helm/swiftdrop \
  --namespace swiftdrop \
  -f infra/helm/swiftdrop/values-dev.yaml \
  --set keycloak.realmImport.configMapName=swiftdrop-realm \
  --wait --timeout 15m
```

- [x] In a small overlay, set `nodeSelector: { role: apps }` on `web`, `api-gateway`, and `pricing-service`, and upgrade again. That overlay is `infra/helm/swiftdrop/values-two-node.yaml`. It also pins Postgres, Redis, and Keycloak to `role: data`. `kubectl -n swiftdrop get pods -o wide` showed the app pods on the apps node and the datastores on the data node.

**Check:** from your laptop, with the hosts file from phase 4:

```sh
DEMO_BASE_URL=http://api.swiftdrop.local/api/v1 ./scripts/demo-m2-pricing.sh
```

The quote page is `http://swiftdrop.local/quote`.

## Phase 6 — Stop and start, addresses unchanged

- [x] Stop both instances from the console or CLI.
- [x] Start both instances.
- [x] Wait until `kubectl get nodes` shows both `Ready`. k3s comes back with the disks; you do not reinstall it.

**Check:** both public addresses still match the Elastic IPs you wrote down in phase 2, and the quote command from phase 5 succeeds again.

## Phase 7 — Full system, one group at a time

`values-full.yaml` is applied on top of `values-dev.yaml`. It enables Kafka (`kafka:9092`), MongoDB, RustFS (`object-storage:9000`), Mailpit, and every service except `ai-assistant-service`.

- [x] Create the topic ConfigMap and the three Mongo secrets, as `scripts/kind-deploy.sh` does when `SWIFTDROP_PROFILE=full`:
  - ConfigMap `swiftdrop-kafka-topics` from `infra/kafka/create-topics.sh`
  - Secrets `swiftdrop-tracking-mongo`, `swiftdrop-notification-mongo`, `swiftdrop-webhook-mongo`, each with `MONGO_URI` using host `mongodb`
- [x] Build and import the remaining images on the apps node. Pull Kafka, MongoDB, RustFS, and Mailpit into the data node.
- [x] Upgrade with both values files, and `values-two-node.yaml` after them:

```sh
helm upgrade --install swiftdrop infra/helm/swiftdrop \
  --namespace swiftdrop \
  -f infra/helm/swiftdrop/values-dev.yaml \
  -f infra/helm/swiftdrop/values-full.yaml \
  -f infra/helm/swiftdrop/values-two-node.yaml \
  --set keycloak.realmImport.configMapName=swiftdrop-realm \
  --wait --timeout 20m
```

- [x] Watch pods in this order, and do not debug the next group until the current one is `Running`:
  1. `kafka`, `mongodb`, `object-storage`, `mailpit`
  2. `user-service`, `merchant-service`
  3. `parcel-service`
  4. `dispatch-service`, `reporting-service`
  5. `tracking-service`, `notification-service`, `webhook-service`
- [x] Leave `ai-assistant-service` off until `GEMINI_API_KEY` or `OPENAI_API_KEY` is in `swiftdrop-app-secrets`. The chart comment says the same thing.
- [x] Leave OSRM off.
- [x] In `values-two-node.yaml`, turn off the parcel and tracking autoscalers. On this 2 vCPU node they add pods while the JVMs are still starting, and the node stays at full CPU. Pricing stays at two replicas for the load-balancing check.

**Check:** `kubectl -n swiftdrop get pods` shows the full set ready, and `kubectl -n swiftdrop get svc kafka` exists. Kafka in the cluster is the broker the marking line asks for.

## Phase 8 — Show orchestration and load balancing

Pricing already has two replicas. Use those. Do not enable the production autoscaler.

- [x] Delete one running pricing pod by name. The label selector matches both replicas, so do not use it alone. A replacement pod reaches `Running` on its own.
- [x] Send twenty quote requests:

```sh
for i in $(seq 1 20); do
  curl -s -o /dev/null -X POST http://api.swiftdrop.local/api/v1/pricing/quotes \
    -H 'content-type: application/json' \
    -d '{"fromDistrict":"Colombo","toDistrict":"Kandy","weightKg":2,"sizeClass":"M","serviceLevel":"STANDARD"}'
done
```

- [x] Both pods received quotes. The service does not write a log line per quote. The count is `http_server_requests_seconds_count` for `uri="/api/v1/pricing/quotes"` on each pod's `/actuator/prometheus`. After twenty requests through the gateway, then twenty more straight to the Service, the two pods showed 30 and 10. The gateway keeps one connection, so a single client through the public URL can land entirely on one pod.

**Check:** one deleted pod came back, and both pricing pods appear in the logs.

## Phase 9 — After the demo

- [x] Stop both instances. Do not terminate them, and do not release the Elastic IPs.
- [ ] The next demo starts at phase 6: start the instances, wait for the nodes, open the quote page once, then present.

## Phase 10 — Later

The platform node is not part of this demo. When you want Grafana, Loki, Tempo, and Argo CD, follow [03-later-platform-node.md](03-later-platform-node.md).
