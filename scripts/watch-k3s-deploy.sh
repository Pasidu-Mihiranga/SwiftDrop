#!/usr/bin/env bash
# On the apps node, pull deploy/k3s-two-node and update only what changed.
#
# The repository is public, so this uses an outbound git fetch. Nothing has to
# accept SSH from GitHub. The first run records the current commit and does not
# rebuild. The next push rebuilds the services that commit touched.
set -euo pipefail

repo_url="${SWIFTDROP_REPO_URL:-https://github.com/Pasidu-Mihiranga/SwiftDrop.git}"
branch="${SWIFTDROP_BRANCH:-deploy/k3s-two-node}"
dir="${SWIFTDROP_CHECKOUT:-/opt/swiftdrop}"
state_file="${SWIFTDROP_STATE:-/var/lib/swiftdrop/deployed-sha}"
lock_file="${SWIFTDROP_LOCK:-/var/lib/swiftdrop/deploy.lock}"

mkdir -p "$(dirname "${state_file}")"
exec 9>"${lock_file}"
if ! flock -n 9; then
  echo "A deploy is already running."
  exit 0
fi

if [ ! -d "${dir}/.git" ]; then
  git clone --branch "${branch}" "${repo_url}" "${dir}"
fi

cd "${dir}"
git fetch --prune origin "${branch}"
git checkout --force "origin/${branch}"
new="$(git rev-parse HEAD)"
old=""
if [ -f "${state_file}" ]; then
  old="$(tr -d '[:space:]' < "${state_file}")"
fi

if [ "${new}" = "${old}" ]; then
  echo "Already deployed ${new}."
  exit 0
fi

if [ -n "${old}" ] && git cat-file -e "${old}^{commit}" 2>/dev/null; then
  bash scripts/deploy-k3s-changed.sh "${old}" "${new}"
else
  echo "Recording ${new} as the deployed commit. The next push will update changed services."
fi

printf '%s\n' "${new}" > "${state_file}"
echo "Deployed ${new}."
