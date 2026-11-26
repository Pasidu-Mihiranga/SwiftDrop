# SwiftDrop AWS Terraform

Creates the two-node network from `doc/deploy/01-two-node-architecture.md`: one VPC, one public subnet, two `m7i-flex.large` instances, and one Elastic IP on each instance. Nothing here installs k3s. That is the next phase in `doc/deploy/02-phase-checklist.md`.

`terraform apply` starts billing. Two running instances are about $0.20 per hour. Each Elastic IP costs about $3.65 per month while its instance is stopped. Do not apply until the manual steps below are done.

## Account

The first build used account `866409326665` in `ap-south-1`. That account is suspended. Build again in the new account, region `ap-southeast-2` (Sydney). Terraform uses the AWS CLI profile `swiftdrop`, not the default keys from the old account.

The old state, key, and `terraform.tfvars` are in `suspended-mumbai/`. Do not copy them back. A new apply creates a new key and new Elastic IPs.

## Before apply

Sign in to the new account in the console with the Google login. Create an IAM user named `swiftdrop-deploy` and attach the policy in `doc/deploy/02-phase-checklist.md`. Create an access key for that user and run `aws configure --profile swiftdrop` with region `ap-southeast-2`. Do not create an access key for the root user.

Then confirm the caller and the instance type:

```sh
aws sts get-caller-identity --profile swiftdrop
aws ec2 describe-instance-types --region ap-southeast-2 --profile swiftdrop --instance-types m7i-flex.large
```

The account id in the first command must not be `866409326665`. In the console, Service Quotas, Amazon EC2, "Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances" for `ap-southeast-2` must be at least 4. Add billing alerts before a long run.

## Apply

```sh
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`. Set `admin_cidr` to your public IP and `/32`. Find the IP with `curl -4 ifconfig.me`. Leave `region` as `ap-southeast-2` and `aws_profile` as `swiftdrop`.

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
aws ec2 stop-instances --region ap-southeast-2 --profile swiftdrop --instance-ids <apps-id> <data-id>
```
