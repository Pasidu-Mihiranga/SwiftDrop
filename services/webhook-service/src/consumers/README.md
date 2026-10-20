# Kafka consumers

Consumers subscribe to SwiftDrop topics through `startEventConsumer` from `@swiftdrop/common`, which
handles parsing, idempotency (duplicate deliveries are skipped), retries and the dead-letter topic.

```ts
await startEventConsumer(kafka, {
  groupId: "webhook-service",
  consumerName: "webhook-service-status",
  topics: [Topics.PARCEL_STATUS_CHANGED],
  handlers: { ParcelStatusChanged: (event) => service.onStatusChanged(event.data as ParcelStatusChanged) },
  idempotency: new MongoIdempotencyStore(db.collection("processed_events")),
  deadLetterTopic: Topics.NOTIFICATION_DEAD_LETTER,
  logger,
});
```

Keep handlers thin: translate the event and call the feature service. Event shapes are defined in
`/contracts/events` (JSON Schema) and typed in `@swiftdrop/common`.
