# Kafka operations (M5)

Run `docker compose --profile infra up -d`; topic initialization is automatic and idempotent.
The `kafka-init` service runs `create-topics.sh`; rerun it with
`docker compose --profile infra run --rm kafka-init` after changing the catalogue.

Kafka UI is available at http://localhost:8088 (or `KAFKA_UI_HOST_PORT`). The compose-managed
Kafbat UI connects to `kafka:19092`. Host clients use `localhost:9092` or `KAFKA_HOST_PORT`.
Do not use the host listener from inside a container.

Topics use `swiftdrop.<domain>.<fact>.v1`. An incompatible contract gets a new version/topic.
The created, status-changed and parcel-assigned topics have six partitions and seven-day
retention; records use the parcel UUID string as their key. Matching keys maintain order
within each topic partition; Kafka does not order events across topics. The dispatch projection
therefore handles status-before-create and ignores older status timestamps. Production replication
is configured through `TOPIC_REPLICATION_FACTOR`; the local single broker uses one replica.

Every value is an EventEnvelope with eventId, eventType, eventVersion, occurredAt, producer,
optional correlationId and data. Never key records by a person's name, email or phone.
Consumers use distinct service group IDs; dispatch uses `dispatch-service`. Every dispatch handler
stores its processed-event marker and read-model writes in the same database transaction.
The shared outbox relay publishes assignments with at-least-once delivery.

Start `docker compose --profile infra --profile observability up -d` for Kafka Exporter,
Prometheus and Grafana. The **SwiftDrop - Dispatch consumer lag** dashboard tracks
`sum by (topic) (kafka_consumergroup_lag{consumergroup="dispatch-service"})` using M9's exporter.
A missing series means no committed consumer offset or a scrape issue, not zero lag.
Inspect group offsets and failed application logs before resetting any offsets.

Replay retains processed markers and safely ignores previously handled event IDs. For a complete
rebuild, stop dispatch writers/consumers, clear its projection and corresponding processed markers
in a controlled maintenance operation, then reset the dispatch group to earliest. Assignment is
command-owned local state: retain assignments and outbox history; do not wipe the dispatch database.
Retention limits replay history, so production rebuilds also require a parcel snapshot/backfill.
