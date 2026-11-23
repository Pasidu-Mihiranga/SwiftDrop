#!/bin/sh
# Reloads Grafana's file-provisioned dashboards whenever a JSON file in the dashboards folder is
# added, edited or removed.
#
# Grafana 12 does not notice those changes by itself when the folder is a Docker Desktop bind
# mount (Windows and macOS do not forward file events into the VM, and its own polling did not
# pick up new files either), so without this a new dashboard only appeared after a restart.
# It compares a checksum of the folder every SYNC_INTERVAL seconds and calls the provisioning
# reload API, which re-reads the folder at once. Development compose only.
set -eu

DIR="${DASHBOARDS_DIR:-/dashboards}"
URL="${GRAFANA_URL:-http://grafana:3000}"
INTERVAL="${SYNC_INTERVAL:-10}"

fingerprint() {
  # Names, sizes and contents, so renames and deletions count as changes too.
  find "$DIR" -type f -name '*.json' | sort | while read -r f; do
    printf '%s ' "$f"
    md5sum < "$f"
  done | md5sum | cut -d' ' -f1
}

last=""
while true; do
  now="$(fingerprint)"
  if [ -n "$last" ] && [ "$now" != "$last" ]; then
    if curl -fsS -m 10 -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
         -X POST "$URL/api/admin/provisioning/dashboards/reload" > /dev/null; then
      echo "dashboards changed: reloaded"
    else
      echo "dashboards changed: reload failed, retrying" >&2
      now=""
    fi
  fi
  last="$now"
  sleep "$INTERVAL"
done
