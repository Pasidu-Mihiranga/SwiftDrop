#!/usr/bin/env bash
# Vendor the Helm chart dependencies.
#
# "helm dependency update" is not recursive: each thin service chart vendors the
# swiftdrop-service library chart, and the umbrella chart vendors the thin charts. Run this
# after changing any chart, and before "helm lint", "helm template" or "helm install".
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
helm_dir="${repo_root}/infra/helm"

for chart in "${helm_dir}"/charts/*/; do
  [ -f "${chart}Chart.yaml" ] || continue
  echo "Updating dependencies for $(basename "${chart}")"
  helm dependency update "${chart}" >/dev/null
done

echo "Updating dependencies for the umbrella chart"
helm dependency update "${helm_dir}/swiftdrop" >/dev/null

echo "Chart dependencies vendored."
