package lk.swiftdrop.outbox;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Drops duplicate Kafka deliveries. Call it first, inside the listener's transaction, so the
 * "processed" marker and the handler's own writes commit (or roll back) together:
 *
 * <pre>{@code
 * @KafkaListener(topics = Topics.PARCEL_STATUS_CHANGED, groupId = "dispatch-service")
 * @Transactional
 * public void on(EventEnvelope<ParcelStatusChanged> event) {
 *     if (!idempotency.firstDelivery(event.eventId(), "dispatch-workload")) return;
 *     workload.apply(event.data());
 * }
 * }</pre>
 */
public class IdempotentConsumer {

    private final ProcessedEventRepository repository;

    public IdempotentConsumer(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** @return true the first time {@code consumer} sees {@code eventId}; false for duplicates */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(UUID eventId, String consumer) {
        return repository.insertIfAbsent(eventId, consumer) == 1;
    }
}
