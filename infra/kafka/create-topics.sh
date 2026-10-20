#!/usr/bin/env bash
# Idempotently creates every SwiftDrop Kafka topic. Topic catalogue: doc/01-architecture.md §1.3
# Usage: create-topics.sh <bootstrap-server>
set -euo pipefail

BOOTSTRAP="${1:-kafka:19092}"
KAFKA_TOPICS="${KAFKA_TOPICS_BIN:-/opt/kafka/bin/kafka-topics.sh}"
REPLICATION="${TOPIC_REPLICATION_FACTOR:-1}"

# name|partitions|extra configs (comma separated, optional)
TOPICS=(
  "swiftdrop.parcel.created.v1|6|retention.ms=604800000"
  "swiftdrop.parcel.status-changed.v1|6|retention.ms=604800000"
  "swiftdrop.dispatch.parcel-assigned.v1|6|retention.ms=604800000"
  "swiftdrop.merchant.lifecycle.v1|3|retention.ms=604800000"
  "swiftdrop.webhook.dead-letter.v1|3|retention.ms=2592000000"
  "swiftdrop.notification.dead-letter.v1|3|retention.ms=2592000000"
)

echo "Waiting for Kafka at ${BOOTSTRAP} ..."
until "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; do sleep 2; done

for entry in "${TOPICS[@]}"; do
  IFS='|' read -r name partitions configs <<<"$entry"
  args=(--bootstrap-server "$BOOTSTRAP" --create --if-not-exists
        --topic "$name" --partitions "$partitions" --replication-factor "$REPLICATION")
  if [[ -n "$configs" ]]; then
    IFS=',' read -ra cfgs <<<"$configs"
    for c in "${cfgs[@]}"; do args+=(--config "$c"); done
  fi
  "$KAFKA_TOPICS" "${args[@]}"
done

echo "Topics:"
"$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list
