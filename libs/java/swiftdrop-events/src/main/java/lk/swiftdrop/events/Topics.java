package lk.swiftdrop.events;

/**
 * Kafka topic names. Must match infra/kafka/create-topics.sh and the Node constants in
 * libs/node/swiftdrop-common/src/events/topics.ts. The ".v1" suffix is the contract version.
 */
public final class Topics {

    public static final String PARCEL_CREATED = "swiftdrop.parcel.created.v1";
    public static final String PARCEL_STATUS_CHANGED = "swiftdrop.parcel.status-changed.v1";
    public static final String PARCEL_ASSIGNED = "swiftdrop.dispatch.parcel-assigned.v1";
    public static final String MERCHANT_LIFECYCLE = "swiftdrop.merchant.lifecycle.v1";
    public static final String WEBHOOK_DEAD_LETTER = "swiftdrop.webhook.dead-letter.v1";
    public static final String NOTIFICATION_DEAD_LETTER = "swiftdrop.notification.dead-letter.v1";

    /** Kafka record headers set by producers (values are UTF-8 strings). */
    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_CORRELATION_ID = "correlationId";

    private Topics() {
    }
}
