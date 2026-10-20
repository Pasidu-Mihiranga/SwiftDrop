package lk.swiftdrop.outbox;

import lk.swiftdrop.events.Topics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls the outbox and publishes pending events to Kafka in creation order. Delivery is
 * at-least-once: if the commit fails after a send, the event is sent again and consumers
 * drop the duplicate by eventId.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return number of events published in this run */
    @Scheduled(fixedDelayString = "${swiftdrop.outbox.poll-interval-ms:500}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.markPublished(clock.instant());
                published++;
            } catch (Exception ex) {
                Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                event.recordFailure(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                log.warn("Outbox publish failed for event {} ({}), attempt {}; will retry",
                        event.getId(), event.getEventType(), event.getAttempts());
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // Stop here: later events may belong to the same aggregate and must not overtake this one.
                break;
            }
        }
        if (published > 0) {
            log.debug("Outbox relay published {} event(s)", published);
        }
        return published;
    }

    /** Housekeeping: published rows are only kept for troubleshooting. */
    @Scheduled(cron = "${swiftdrop.outbox.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int deleteOldPublishedEvents() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
        return repository.deletePublishedBefore(cutoff);
    }

    private void send(OutboxEvent event) throws InterruptedException, ExecutionException, TimeoutException {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add(Topics.HEADER_EVENT_ID, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        if (event.getCorrelationId() != null) {
            record.headers().add(Topics.HEADER_CORRELATION_ID, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
    }
}
