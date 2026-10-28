#!/usr/bin/env bash
# Create the local kind cluster and install the Ingress controller.
#
# Run scripts/kind-deploy.sh afterwards to build the images and install the chart.
set -euo pipefail

cluster="swiftdrop"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Colima is not discovered automatically: point Docker at it if nothing else is set.
if [ -z "${DOCKER_HOST:-}" ] && [ -S "${HOME}/.colima/default/docker.sock" ]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  echo "Using Colima at ${DOCKER_HOST}"
fi

if kind get clusters 2>/dev/null | grep -qx "${cluster}"; then
  echo "Cluster ${cluster} already exists."
else
  kind create cluster --config "${repo_root}/infra/helm/kind-cluster.yaml"
fi

kubectl config use-context "kind-${cluster}"

echo "Installing ingress-nginx"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

echo "Waiting for the Ingress controller to become ready"
kubectl -n ingress-nginx wait --for=condition=available deploy/ingress-nginx-controller --timeout=300s

cat <<'EOF'

Cluster ready. Add the hosts once, then run scripts/kind-deploy.sh:

  sudo sh -c 'echo "127.0.0.1 swiftdrop.local api.swiftdrop.local auth.swiftdrop.local grafana.swiftdrop.local" >> /etc/hosts'
EOF
