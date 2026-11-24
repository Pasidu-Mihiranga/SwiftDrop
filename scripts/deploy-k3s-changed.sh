#!/usr/bin/env bash
# Rebuild and restart only the services whose files changed, on the apps node.
#
# Usage, from the repository root:
#   scripts/deploy-k3s-changed.sh <before-sha> <after-sha>
#
# Images stay on this machine (imagePullPolicy Never). GitHub Actions reaches this
# script through a self-hosted runner, so port 22 is not opened to the internet.
set -euo pipefail

before="${1:-}"
after="${2:-HEAD}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

namespace="${SWIFTDROP_NAMESPACE:-swiftdrop}"
tag="${IMAGE_TAG:-0.1.0}"
kctl=(sudo k3s kubectl -n "${namespace}")

if [ -z "${before}" ] || [ "${before}" = "0000000000000000000000000000000000000000" ]; then
  before="$(git rev-parse "${after}^")"
fi

mapfile -t changed < <(git diff --name-only "${before}" "${after}")
if [ "${#changed[@]}" -eq 0 ]; then
  echo "No file changes between ${before} and ${after}."
  exit 0
fi

declare -A services=()
helm_change=0
java_libs=0
node_libs=0
python_libs=0

java_services=(api-gateway user-service merchant-service pricing-service parcel-service dispatch-service reporting-service)
node_services=(tracking-service notification-service webhook-service)

for path in "${changed[@]}"; do
  case "${path}" in
    libs/java/*) java_libs=1 ;;
    libs/node/*|contracts/*|package.json|pnpm-lock.yaml|pnpm-workspace.yaml|tsconfig.base.json) node_libs=1 ;;
    libs/python/*) python_libs=1 ;;
    apps/web/*) services[web]=apps/web/Dockerfile ;;
    services/*/*)
      name="${path#services/}"
      name="${name%%/*}"
      if [ -f "services/${name}/Dockerfile" ]; then
        services["${name}"]="services/${name}/Dockerfile"
      fi
      ;;
    infra/helm/*) helm_change=1 ;;
  esac
done

if [ "${java_libs}" -eq 1 ]; then
  for name in "${java_services[@]}"; do
    services["${name}"]="services/${name}/Dockerfile"
  done
fi
if [ "${node_libs}" -eq 1 ]; then
  for name in "${node_services[@]}"; do
    services["${name}"]="services/${name}/Dockerfile"
  done
  services[web]=apps/web/Dockerfile
fi
if [ "${python_libs}" -eq 1 ]; then
  services[ai-assistant-service]=services/ai-assistant-service/Dockerfile
fi

build_one() {
  local name="$1" dockerfile="$2"
  if ! "${kctl[@]}" get deploy "${name}" >/dev/null 2>&1; then
    echo "Skip ${name}: it is not installed in ${namespace}."
    return 0
  fi
  echo "Build swiftdrop/${name}:${tag}"
  sudo docker build -t "swiftdrop/${name}:${tag}" -f "${dockerfile}" .
  sudo docker save "swiftdrop/${name}:${tag}" | sudo k3s ctr images import -
  "${kctl[@]}" rollout restart "deploy/${name}"
  "${kctl[@]}" rollout status "deploy/${name}" --timeout=180s
}

if [ "${helm_change}" -eq 1 ]; then
  echo "Helm values or templates changed. Upgrading the release."
  ./scripts/helm-deps.sh
  sudo KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm upgrade swiftdrop infra/helm/swiftdrop \
    --namespace "${namespace}" \
    -f infra/helm/swiftdrop/values-dev.yaml \
    -f infra/helm/swiftdrop/values-full.yaml \
    -f infra/helm/swiftdrop/values-two-node.yaml \
    --set keycloak.realmImport.configMapName=swiftdrop-realm \
    --timeout 15m
fi

for name in "${!services[@]}"; do
  build_one "${name}" "${services[${name}]}"
done

echo "Deploy finished."
