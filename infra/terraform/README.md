# SwiftDrop AWS Terraform

Creates the two-node network from `doc/deploy/01-two-node-architecture.md`: one VPC, one public subnet, two `m7i-flex.large` instances, and one Elastic IP on each instance. Nothing here installs k3s. That is the next phase in `doc/deploy/02-phase-checklist.md`.

`terraform apply` starts billing. Two running instances are about $0.20 per hour. Each Elastic IP costs about $3.65 per month while its instance is stopped. Do not apply until the manual steps below are done.

## What the AWS CLI can already do

Checked with the `developer` user in account `866409326665`, region `ap-south-1`:

- `aws sts get-caller-identity` succeeds.
- Creating a VPC, allocating an Elastic IP, and launching `m7i-flex.large` are allowed (dry-run only; nothing was created).
- Ubuntu 24.04 amd64 is available. `m7i-flex.large` is free-tier eligible, 2 vCPU, 8 GiB.
- A default VPC already exists (`172.31.0.0/16`). This Terraform creates a separate VPC, `10.20.0.0/16`, so a later destroy does not touch the default one.

## Manual steps this user cannot do

The `developer` user is denied these calls. Do them in the AWS console while signed in as the account root or an admin:

1. Billing alerts at $50 and $150 of credits. Budgets is denied for `developer`.
2. Confirm the vCPU quota can run two `m7i-flex.large` at once (4 vCPU). Service Quotas is denied for `developer`. In the console open Service Quotas, Amazon EC2, "Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances" in `ap-south-1`. The number must be at least 4.
3. Attaching the tighter IAM policy from `doc/deploy/02-phase-checklist.md` is optional. IAM is denied for `developer`, and that user can already launch what this Terraform creates. Do not switch the CLI to a weaker user until an admin has attached that policy.

## Apply

```sh
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and set `admin_cidr` to your public IP and `/32`. Find the IP with `curl -4 ifconfig.me`.

```sh
terraform init
terraform plan
terraform apply
```

`apply` prints both Elastic IPs. SSH uses `swiftdrop-key.pem` in this directory. That file is gitignored.

```sh
ssh -i swiftdrop-key.pem ubuntu@<apps_elastic_ip>
```

Stop the instances when you are finished for the day. Do not release the Elastic IPs if you want the same addresses next time.

```sh
aws ec2 stop-instances --region ap-south-1 --instance-ids <apps-id> <data-id>
```
