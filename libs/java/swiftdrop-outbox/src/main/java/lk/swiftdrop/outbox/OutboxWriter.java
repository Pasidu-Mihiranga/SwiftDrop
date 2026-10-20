package lk.swiftdrop.outbox;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.EventPayload;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The only way application code publishes events. Call it inside the same transaction as the
 * business change — it refuses to run without one — so the change and the event commit together.
 *
 * <pre>{@code
 * @Transactional
 * public Parcel create(...) {
 *     Parcel parcel = parcels.save(...);
 *     outbox.publish(new ParcelCreated(...));
 *     return parcel;
 * }
 * }</pre>
 */
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String producer;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper, String producer) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T extends EventPayload> EventEnvelope<T> publish(T payload) {
        String correlationId = MDC.get("correlationId");
        EventEnvelope<T> envelope = EventEnvelope.wrap(payload, producer, correlationId);
        repository.save(new OutboxEvent(
                envelope.eventId(),
                payload.partitionKey(),
                envelope.eventType(),
                payload.topic(),
                payload.partitionKey(),
                objectMapper.writeValueAsString(envelope),
                correlationId,
                envelope.occurredAt()));
        return envelope;
    }
}
