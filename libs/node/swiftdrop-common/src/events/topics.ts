/**
 * Kafka topic names — must match infra/kafka/create-topics.sh and
 * libs/java/swiftdrop-events/src/main/java/lk/swiftdrop/events/Topics.java.
 */
export const Topics = {
  PARCEL_CREATED: "swiftdrop.parcel.created.v1",
  PARCEL_STATUS_CHANGED: "swiftdrop.parcel.status-changed.v1",
  PARCEL_ASSIGNED: "swiftdrop.dispatch.parcel-assigned.v1",
  MERCHANT_LIFECYCLE: "swiftdrop.merchant.lifecycle.v1",
  WEBHOOK_DEAD_LETTER: "swiftdrop.webhook.dead-letter.v1",
  NOTIFICATION_DEAD_LETTER: "swiftdrop.notification.dead-letter.v1",
} as const;

export type Topic = (typeof Topics)[keyof typeof Topics];

/** Kafka record headers set by every producer. */
export const EventHeaders = {
  EVENT_ID: "eventId",
  EVENT_TYPE: "eventType",
  CORRELATION_ID: "correlationId",
} as const;
