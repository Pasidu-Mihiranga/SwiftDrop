#!/usr/bin/env bash
# Show an evaluator the three Kubernetes behaviours that matter here:
#   1. a deleted pod is created again
#   2. raising the replica count adds a pod
#   3. lowering the replica count removes a pod
#
# The service is checked before and after each step. The starting replica
# count is restored on the way out, including when a step fails.
#
# The apps machine is 2 vCPU and 8 GB, so this demo never goes above 2
# replicas and it refuses databases, Keycloak, and the message broker.
#
# From the laptop (kubectl cannot reach the cluster port):
#   ./scripts/demo-kubernetes.sh
# On the apps machine:
#   ./scripts/demo-kubernetes.sh --on-node
#
# Optional:
#   DEMO_DEPLOYMENT=web ./scripts/demo-kubernetes.sh
#   SWIFTDROP_SSH_HOST=3.111.117.223 SWIFTDROP_SSH_KEY=infra/terraform/swiftdrop-key.pem

set -euo pipefail

if [[ "${1:-}" != "--on-node" ]]; then
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  host="${SWIFTDROP_SSH_HOST:-3.111.117.223}"
  key="${SWIFTDROP_SSH_KEY:-${root}/infra/terraform/swiftdrop-key.pem}"
  if [[ ! -f "${key}" ]]; then
    echo "SSH key not found at ${key}" >&2
    exit 1
  fi
  echo "Running the demo on ${host}."
  ssh -i "${key}" -o ConnectTimeout=20 -o StrictHostKeyChecking=accept-new "ubuntu@${host}" \
    "DEMO_DEPLOYMENT='${DEMO_DEPLOYMENT:-}' DEMO_NAMESPACE='${DEMO_NAMESPACE:-}' DEMO_MAX_REPLICAS='${DEMO_MAX_REPLICAS:-}' bash -s -- --on-node" \
    < "${BASH_SOURCE[0]}"
  exit $?
fi

namespace="${DEMO_NAMESPACE:-swiftdrop}"
deployment="${DEMO_DEPLOYMENT:-notification-service}"
max_replicas="${DEMO_MAX_REPLICAS:-2}"
if [[ "${max_replicas}" -gt 2 ]]; then
  echo "DEMO_MAX_REPLICAS is capped at 2 on this cluster." >&2
  max_replicas=2
fi

case "${deployment}" in
  postgres|redis|kafka|mongodb|object-storage|keycloak|mailpit)
    echo "Refusing ${deployment}. That is a database or a single sign-in server, not a replica demo." >&2
    exit 1
    ;;
esac

k() { sudo k3s kubectl -n "${namespace}" "$@"; }

original=""
restore() {
  if [[ -n "${original}" ]]; then
    echo
    echo "Restoring ${deployment} to ${original} replica(s)."
    k scale "deployment/${deployment}" --replicas="${original}" >/dev/null
  fi
}
trap restore EXIT

ready_count() {
  k get "deployment/${deployment}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true
}

live_count() {
  # A pod that is shutting down still appears in the list. It is not a replica.
  k get pods -l "app.kubernetes.io/name=${deployment}" \
    -o jsonpath='{range .items[*]}{.metadata.deletionTimestamp}{"\n"}{end}' \
    | awk 'NF == 0 { count++ } END { print count + 0 }'
}

wait_ready() {
  local want="$1"
  local deadline=$((SECONDS + 180))
  local ready=""
  local live=""
  while (( SECONDS < deadline )); do
    ready="$(ready_count)"
    ready="${ready:-0}"
    live="$(live_count)"
    echo "  ready ${ready} of ${want}"
    if [[ "${ready}" == "${want}" && "${live}" == "${want}" ]]; then
      return 0
    fi
    sleep 3
  done
  echo "Timed out waiting for ${want} ready pod(s)." >&2
  k get pods -l "app.kubernetes.io/name=${deployment}" -o wide >&2 || true
  return 1
}

pod_names() {
  k get pods -l "app.kubernetes.io/name=${deployment}" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'
}

show_pods() {
  k get pods -l "app.kubernetes.io/name=${deployment}" \
    -o jsonpath='{range .items[*]}{.metadata.deletionTimestamp}{"|"}{.metadata.name}{"|"}{.status.containerStatuses[0].ready}{"|"}{.spec.nodeName}{"|"}{.status.podIP}{"\n"}{end}' \
    | awk -F'|' '
      $1 == "" { printf "%s  ready=%s  node=%s  ip=%s\n", $2, $3, $4, $5; live++ }
      $1 != "" { stopping++ }
      END {
        if (stopping > 0) printf "%d pod(s) are shutting down and are no longer in the service\n", stopping
      }'
}

endpoint_ips() {
  k get endpoints "${deployment}" -o jsonpath='{range .subsets[*].addresses[*]}{.ip}{"\n"}{end}' 2>/dev/null
}

probe() {
  # Any other running app can call the Service by name. The pod IP is not used.
  if k exec "deployment/web" -- wget -qO- --timeout=5 "http://${deployment}:${1}/health/live" >/dev/null 2>&1 \
    || k exec "deployment/web" -- wget -qO- --timeout=5 "http://${deployment}:${1}/actuator/health/liveness" >/dev/null 2>&1; then
    echo "  service answered"
  else
    echo "  service did not answer yet"
    return 1
  fi
}

service_port() {
  k get "service/${deployment}" -o jsonpath='{.spec.ports[0].port}'
}

echo
echo "Kubernetes demo: ${deployment} in ${namespace}"
echo "Delete a pod, raise the replica count, then lower it. The count is restored at the end."
echo

echo "1. Both machines are in the cluster"
sudo k3s kubectl get nodes

if k get "hpa/${deployment}" >/dev/null 2>&1; then
  echo "An autoscaler already owns ${deployment}. Scale that by hand only after the autoscaler is off." >&2
  exit 1
fi

original="$(k get "deployment/${deployment}" -o jsonpath='{.spec.replicas}')"
port="$(service_port)"
echo
echo "2. Starting point: ${original} replica(s), ceiling ${max_replicas}"
show_pods
echo "Service addresses:"
endpoint_ips
probe "${port}"

echo
echo "3. Delete one pod. Kubernetes must create a replacement."
before="$(pod_names | head -n 1)"
echo "  deleting ${before}"
k delete "pod/${before}" --wait=false >/dev/null
wait_ready "${original}"
after="$(pod_names)"
if [[ "${after}" == *"${before}"* && "$(echo "${after}" | wc -l)" -le "${original}" ]]; then
  echo "The deleted pod was not replaced." >&2
  exit 1
fi
echo "  replacement is running"
show_pods
probe "${port}"

if [[ "${original}" -lt "${max_replicas}" ]]; then
  raised=$((original + 1))
  echo
  echo "4. Increase demand: ${original} to ${raised}"
  k scale "deployment/${deployment}" --replicas="${raised}" >/dev/null
  wait_ready "${raised}"
  show_pods
  echo "Service addresses:"
  endpoint_ips
  probe "${port}"

  echo
  echo "5. Decrease demand: ${raised} back to ${original}"
  k scale "deployment/${deployment}" --replicas="${original}" >/dev/null
  wait_ready "${original}"
  show_pods
  echo "Service addresses:"
  endpoint_ips
  probe "${port}"
else
  lowered=$((original - 1))
  if [[ "${lowered}" -lt 1 ]]; then
    echo "Cannot lower a deployment that has no replicas." >&2
    exit 1
  fi
  echo
  echo "4. ${deployment} is already at the ceiling of ${max_replicas}, so demand is lowered first."
  echo "   Decrease demand: ${original} to ${lowered}"
  k scale "deployment/${deployment}" --replicas="${lowered}" >/dev/null
  wait_ready "${lowered}"
  show_pods
  echo "Service addresses:"
  endpoint_ips
  probe "${port}"

  echo
  echo "5. Increase demand: ${lowered} back to ${original}"
  k scale "deployment/${deployment}" --replicas="${original}" >/dev/null
  wait_ready "${original}"
  show_pods
  echo "Service addresses:"
  endpoint_ips
  probe "${port}"
fi

echo
echo "Demo finished. ${deployment} is back to ${original} replica(s)."
original=""
