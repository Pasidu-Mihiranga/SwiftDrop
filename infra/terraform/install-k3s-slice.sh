#!/usr/bin/env bash
# Create the dev-slice namespace, secrets, and Helm release on the apps node.
# Run on the apps node, from a checkout that contains .env. Never prints secret values.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${repo_root}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run with sudo so kubectl can read the k3s kubeconfig." >&2
  exit 1
fi

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
namespace="${SWIFTDROP_NAMESPACE:-swiftdrop}"
profile="${SWIFTDROP_PROFILE:-dev}"

[ -f .env ] || { echo "No .env found." >&2; exit 1; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a

kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "${namespace}" create configmap swiftdrop-realm \
  --from-file=realm-swiftdrop.json=infra/keycloak/realm-swiftdrop.json \
  --dry-run=client -o yaml | kubectl apply -f -

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
add MONGO_ROOT_USER "${MONGO_ROOT_USER:-}"
add MONGO_ROOT_PASSWORD "${MONGO_ROOT_PASSWORD:-}"
add S3_ACCESS_KEY "${S3_ACCESS_KEY:-}"
add S3_SECRET_KEY "${S3_SECRET_KEY:-}"
create_secret swiftdrop-platform-secrets

add DB_PASSWORD "${SWIFTDROP_DB_PASSWORD:-}"
add REDIS_PASSWORD "${REDIS_PASSWORD:-}"
add REDIS_URL "redis://:${REDIS_PASSWORD:-}@redis:6379"
add PII_ENCRYPTION_KEY "${PII_ENCRYPTION_KEY:-}"
add WEBHOOK_SECRET_KEY "${PII_ENCRYPTION_KEY:-}"
add KC_USER_SERVICE_CLIENT_SECRET "${KC_USER_SERVICE_CLIENT_SECRET:-}"
add KC_MERCHANT_SERVICE_CLIENT_SECRET "${KC_MERCHANT_SERVICE_CLIENT_SECRET:-}"
add S3_ACCESS_KEY "${S3_ACCESS_KEY:-}"
add S3_SECRET_KEY "${S3_SECRET_KEY:-}"
add GEMINI_API_KEY "${GEMINI_API_KEY:-}"
add OPENAI_API_KEY "${OPENAI_API_KEY:-}"
add ASSISTANT_VAULT_SECRET "${ASSISTANT_VAULT_SECRET:-}"
create_secret swiftdrop-app-secrets

add AUTH_SECRET "${AUTH_SECRET:-}"
add AUTH_KEYCLOAK_SECRET "${KC_WEB_CLIENT_SECRET:-}"
create_secret swiftdrop-web-secrets

values_files=(-f infra/helm/swiftdrop/values-dev.yaml -f infra/helm/swiftdrop/values-two-node.yaml)
if [ "${profile}" = "full" ]; then
  kubectl -n "${namespace}" create configmap swiftdrop-kafka-topics \
    --from-file=create-topics.sh=infra/kafka/create-topics.sh \
    --dry-run=client -o yaml | kubectl apply -f -
  for entry in tracking:tracking notification:notifications webhook:webhooks; do
    svc="${entry%%:*}" db="${entry#*:}"
    add MONGO_URI "mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@mongodb:27017/${db}?authSource=admin"
    create_secret "swiftdrop-${svc}-mongo"
  done
  values_files=(-f infra/helm/swiftdrop/values-dev.yaml -f infra/helm/swiftdrop/values-full.yaml -f infra/helm/swiftdrop/values-two-node.yaml)
fi

./scripts/helm-deps.sh
helm upgrade --install swiftdrop infra/helm/swiftdrop \
  --namespace "${namespace}" \
  "${values_files[@]}" \
  --set keycloak.realmImport.configMapName=swiftdrop-realm \
  --wait --timeout 20m

kubectl -n "${namespace}" get pods -o wide
