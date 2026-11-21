package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.Topics;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.infrastructure.messaging.LifecycleEventParser.LifecycleEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Keeps parcel-service's merchant status cache from merchant-service's lifecycle events (design
 * figure 13), so the order API can tell an APPROVED merchant from any other without calling
 * merchant-service.
 *
 * <p>Each event is handled in one transaction: the {@link IdempotentConsumer} marker first, then
 * the cache. A duplicate delivery stops at the marker; an older change is ignored by the cache. If
 * anything fails, both roll back, so the event is not marked as consumed and is delivered again
 * (see {@link lk.swiftdrop.parcel.infrastructure.config.KafkaConsumerConfiguration}).
 */
@Component
public class MerchantLifecycleListener {

    /** The processed_event consumer name for this listener. */
    static final String CONSUMER = "parcel-merchant-status";
    /** The listener container's id (not its consumer group). */
    public static final String LISTENER_ID = "merchant-lifecycle";

    private static final Logger log = LoggerFactory.getLogger(MerchantLifecycleListener.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final LifecycleEventParser parser;
    private final IdempotentConsumer idempotency;
    private final MerchantStatusCache cache;
    private final TransactionOperations transaction;

    public MerchantLifecycleListener(LifecycleEventParser parser, IdempotentConsumer idempotency,
                                     MerchantStatusCache cache, TransactionOperations transaction) {
        this.parser = parser;
        this.idempotency = idempotency;
        this.cache = cache;
        this.transaction = transaction;
    }

    /** {@code idIsGroup = false}: the consumer group is spring.kafka.consumer.group-id (parcel-service), not the id. */
    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = Topics.MERCHANT_LIFECYCLE)
    public void onLifecycleChanged(ConsumerRecord<String, String> record) {
        header(record, Topics.HEADER_CORRELATION_ID).ifPresent(id -> MDC.put(MDC_CORRELATION_ID, id));
        try {
            Optional<LifecycleEvent> event = parser.parse(record.value());
            if (event.isEmpty()) {
                log.debug("Skipped {} at offset {}: not a MerchantLifecycleChanged v1", record.topic(), record.offset());
                return;
            }
            handle(event.get());
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private void handle(LifecycleEvent event) {
        transaction.executeWithoutResult(status -> {
            if (!idempotency.firstDelivery(event.eventId(), CONSUMER)) {
                log.debug("Duplicate lifecycle event {} ignored", event.eventId());
                return;
            }
            boolean changed = cache.apply(event.standing());
            log.info("Merchant {} is {} as of {}{}", event.standing().merchantId(), event.standing().status(),
                    event.standing().changedAt(), changed ? "" : " (older than the stored status, ignored)");
        });
    }

    private static Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? Optional.empty() : Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }
}
