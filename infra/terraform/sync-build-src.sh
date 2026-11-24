#!/bin/bash
set -euo pipefail
KEY=/Users/oneionei/Documents/MyProjects/EAD/SwiftDrop/infra/terraform/swiftdrop-key.pem
ROOT=/Users/oneionei/Documents/MyProjects/EAD/SwiftDrop
RSH="ssh -i ${KEY} -o StrictHostKeyChecking=accept-new"
HOST=ubuntu@3.111.117.223

$RSH "$HOST" 'mkdir -p ~/SwiftDrop/infra ~/SwiftDrop/scripts ~/SwiftDrop/apps ~/SwiftDrop/libs'

rsync -az -e "$RSH" \
  "$ROOT/pom.xml" "$ROOT/package.json" "$ROOT/pnpm-lock.yaml" \
  "$ROOT/pnpm-workspace.yaml" "$ROOT/tsconfig.base.json" "$ROOT/.env" \
  "$HOST:~/SwiftDrop/"

rsync -az --exclude target --exclude node_modules --exclude .venv --exclude __pycache__ --exclude .git -e "$RSH" \
  "$ROOT/libs/java" "$ROOT/services" "$HOST:~/SwiftDrop/"

rsync -az --exclude node_modules --exclude .next -e "$RSH" \
  "$ROOT/apps/web" "$HOST:~/SwiftDrop/apps/"

rsync -az -e "$RSH" \
  "$ROOT/infra/helm" "$ROOT/infra/keycloak" "$ROOT/infra/kafka" "$ROOT/infra/postgres" \
  "$HOST:~/SwiftDrop/infra/"

rsync -az -e "$RSH" "$ROOT/scripts/" "$HOST:~/SwiftDrop/scripts/"

$RSH "$HOST" 'test -f ~/SwiftDrop/.env && test -f ~/SwiftDrop/services/pricing-service/Dockerfile && test -f ~/SwiftDrop/infra/helm/swiftdrop/values-dev.yaml && du -sh ~/SwiftDrop && echo sync-ok'
