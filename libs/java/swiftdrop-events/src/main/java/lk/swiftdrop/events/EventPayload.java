package lk.swiftdrop.events;

/**
 * Implemented by every event payload ({@code data} of the envelope). The methods are not
 * record components, so they are not serialised into the payload.
 */
public interface EventPayload {

    /** Envelope {@code eventType}, e.g. {@code ParcelStatusChanged}. */
    String eventType();

    /** Envelope {@code eventVersion}. Bump it (and the topic suffix) only for breaking changes. */
    int eventVersion();

    /** Kafka topic this event is published to. */
    String topic();

    /** Kafka message key. Events with the same key are delivered in order. */
    String partitionKey();
}
