#!/usr/bin/env bash
# Build the images, load them into kind, create the Secrets, and install the umbrella chart.
#
# Credentials come from the git-ignored .env (create it with "pnpm env:init"). They are
# passed to kubectl without being printed, and never written into a values file.
set -euo pipefail

cluster="swiftdrop"
namespace="${SWIFTDROP_NAMESPACE:-swiftdrop}"
release="swiftdrop"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

if [ -z "${DOCKER_HOST:-}" ] && [ -S "${HOME}/.colima/default/docker.sock" ]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
fi

[ -f .env ] || { echo "No .env found. Run 'pnpm env:init' first." >&2; exit 1; }
set -a; . ./.env; set +a

tag="${IMAGE_TAG:-0.1.0}"
# The slice values-dev.yaml enables. Add a service here when you enable it there.
services=("pricing-service" "api-gateway")

echo "== Building images (tag ${tag})"
for svc in "${services[@]}"; do
  docker build -q -t "swiftdrop/${svc}:${tag}" -f "services/${svc}/Dockerfile" . >/dev/null
  echo "  swiftdrop/${svc}:${tag}"
done
docker build -q -t "swiftdrop/web:${tag}" -f apps/web/Dockerfile . >/dev/null
echo "  swiftdrop/web:${tag}"

echo "== Loading images into kind"
# With the containerd image store (Colima, and Docker Desktop with the containerd
# snapshotter enabled) "docker save" writes a multi-platform archive that kind cannot
# import: it fails with "content digest ... not found". Saving this platform explicitly
# produces an archive kind accepts, so prefer that and fall back to the direct load.
arch="$(docker version --format '{{.Server.Arch}}')"
load_image() {
  local image="$1" archive
  archive="$(mktemp -t swiftdrop-image)"
  if docker save --platform "linux/${arch}" "${image}" -o "${archive}" 2>/dev/null; then
    kind load image-archive --name "${cluster}" "${archive}"
  else
    kind load docker-image --name "${cluster}" "${image}"
  fi
  rm -f "${archive}"
  echo "  ${image}"
}

for image in postgres:16.15-alpine redis:7.4.11-alpine quay.io/keycloak/keycloak:26.8.1 "swiftdrop/web:${tag}"; do
  load_image "${image}"
done
for svc in "${services[@]}"; do
  load_image "swiftdrop/${svc}:${tag}"
done

echo "== Namespace and configuration"
kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f -

# Realm: the repository file stays the single source of truth; the chart only mounts it.
kubectl -n "${namespace}" create configmap swiftdrop-realm \
  --from-file=realm-swiftdrop.json=infra/keycloak/realm-swiftdrop.json \
  --dry-run=client -o yaml | kubectl apply -f -

# Builds "--from-literal=KEY=value" arguments, skipping anything unset in .env.
literals=()
add() {
  local key="$1" value="${2:-}"
  if [ -n "${value}" ]; then
    literals+=("--from-literal=${key}=${value}")
  fi
}
create_secret() {
  local name="$1"
  kubectl -n "${namespace}" create secret generic "${name}" "${literals[@]}" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "  ${name} (${#literals[@]} keys)"
  literals=()
}

add POSTGRES_PASSWORD "${POSTGRES_PASSWORD:-}"
add SWIFTDROP_DB_PASSWORD "${SWIFTDROP_DB_PASSWORD:-}"
add KEYCLOAK_DB_PASSWORD "${KEYCLOAK_DB_PASSWORD:-}"
add KC_DB_PASSWORD "${KEYCLOAK_DB_PASSWORD:-}"
add REDIS_PASSWORD "${REDIS_PASSWORD:-}"
add KC_BOOTSTRAP_ADMIN_USERNAME "${KEYCLOAK_ADMIN_USER:-}"
add KC_BOOTSTRAP_ADMIN_PASSWORD "${KEYCLOAK_ADMIN_PASSWORD:-}"
add KC_WEB_CLIENT_SECRET "${KC_WEB_CLIENT_SECRET:-}"
add KC_USER_SERVICE_CLIENT_SECRET "${KC_USER_SERVICE_CLIENT_SECRET:-}"
add KC_MERCHANT_SERVICE_CLIENT_SECRET "${KC_MERCHANT_SERVICE_CLIENT_SECRET:-}"
add KC_DEMO_MERCHANT_CLIENT_SECRET "${KC_DEMO_MERCHANT_CLIENT_SECRET:-}"
add KC_SEED_USER_PASSWORD "${KC_SEED_USER_PASSWORD:-}"
add GOOGLE_CLIENT_ID "${GOOGLE_CLIENT_ID:-not-configured}"
add GOOGLE_CLIENT_SECRET "${GOOGLE_CLIENT_SECRET:-not-configured}"
create_secret swiftdrop-platform-secrets

add DB_PASSWORD "${SWIFTDROP_DB_PASSWORD:-}"
add REDIS_PASSWORD "${REDIS_PASSWORD:-}"
add PII_ENCRYPTION_KEY "${PII_ENCRYPTION_KEY:-}"
add S3_ACCESS_KEY "${S3_ACCESS_KEY:-}"
add S3_SECRET_KEY "${S3_SECRET_KEY:-}"
add GEMINI_API_KEY "${GEMINI_API_KEY:-}"
add OPENAI_API_KEY "${OPENAI_API_KEY:-}"
create_secret swiftdrop-app-secrets

add AUTH_SECRET "${AUTH_SECRET:-}"
add AUTH_KEYCLOAK_SECRET "${KC_WEB_CLIENT_SECRET:-}"
create_secret swiftdrop-web-secrets

echo "== Installing the chart"
./scripts/helm-deps.sh >/dev/null
helm upgrade --install "${release}" infra/helm/swiftdrop \
  --namespace "${namespace}" \
  -f infra/helm/swiftdrop/values-dev.yaml \
  --set global.imageTag="${tag}" \
  --set keycloak.realmImport.configMapName=swiftdrop-realm \
  --wait --timeout 10m

kubectl -n "${namespace}" get pods
