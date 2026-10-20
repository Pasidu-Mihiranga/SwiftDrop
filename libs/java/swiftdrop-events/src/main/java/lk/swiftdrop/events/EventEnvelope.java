package lk.swiftdrop.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wrapper for every event on Kafka (schema: contracts/events/envelope.schema.json).
 *
 * @param eventId       unique id; consumers use it to drop duplicates
 * @param eventType     payload type name
 * @param eventVersion  payload schema version
 * @param occurredAt    when the business fact happened
 * @param correlationId id of the user request that caused the event (may be null for scheduled jobs)
 * @param producer      name of the producing service
 * @param data          the payload
 */
public record EventEnvelope<T>(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                               String correlationId, String producer, T data) {

    /** Wraps a payload in a new envelope with a fresh id and the current time. */
    public static <T extends EventPayload> EventEnvelope<T> wrap(T payload, String producer, String correlationId) {
        return new EventEnvelope<>(UUID.randomUUID(), payload.eventType(), payload.eventVersion(), Instant.now(),
                correlationId, producer, payload);
    }
}
