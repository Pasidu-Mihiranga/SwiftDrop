package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.parcel.application.command.UpdateParcelStatusUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Records dispatch's assignments (M5's {@code ParcelAssigned}) on the parcel, so the assigned courier
 * may read and update it (M4). The idempotency marker and the parcel change commit together; a
 * duplicate delivery stops at the marker. Another event type or version is skipped; a message that
 * is not a ParcelAssigned envelope is a {@link MalformedEventException}, which the error handler
 * logs and skips.
 */
@Component
public class ParcelAssignedListener {

    /** The processed_event consumer name for this listener. */
    static final String CONSUMER = "parcel-courier-assignment";
    /** The listener container's id (not its consumer group). */
    public static final String LISTENER_ID = "parcel-assigned";

    private static final Logger log = LoggerFactory.getLogger(ParcelAssignedListener.class);
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ObjectMapper json;
    private final IdempotentConsumer idempotency;
    private final UpdateParcelStatusUseCase statuses;
    private final TransactionOperations transaction;

    public ParcelAssignedListener(ObjectMapper json, IdempotentConsumer idempotency, UpdateParcelStatusUseCase statuses,
                                  TransactionOperations transaction) {
        this.json = json;
        this.idempotency = idempotency;
        this.statuses = statuses;
        this.transaction = transaction;
    }

    /** {@code idIsGroup = false}: the consumer group is spring.kafka.consumer.group-id (parcel-service), not the id. */
    @KafkaListener(id = LISTENER_ID, idIsGroup = false, topics = Topics.PARCEL_ASSIGNED)
    public void onParcelAssigned(ConsumerRecord<String, String> record) {
        Header correlation = record.headers().lastHeader(Topics.HEADER_CORRELATION_ID);
        if (correlation != null) {
            MDC.put(MDC_CORRELATION_ID, new String(correlation.value(), StandardCharsets.UTF_8));
        }
        try {
            JsonNode envelope = read(record.value());
            if (!ParcelAssigned.TYPE.equals(envelope.path("eventType").asString(""))
                    || envelope.path("eventVersion").asInt(-1) != ParcelAssigned.VERSION) {
                log.debug("Skipped {} at offset {}: not a ParcelAssigned v1", record.topic(), record.offset());
                return;
            }
            UUID eventId = uuid(envelope.path("eventId"), "eventId");
            JsonNode data = envelope.path("data");
            UUID parcelId = uuid(data.path("parcelId"), "data.parcelId");
            String courierId = data.path("courierId").asString("");
            if (courierId.isBlank()) {
                throw new MalformedEventException("ParcelAssigned " + eventId + " has no courierId");
            }
            transaction.executeWithoutResult(status -> {
                if (!idempotency.firstDelivery(eventId, CONSUMER)) {
                    log.debug("Duplicate ParcelAssigned {} ignored", eventId);
                    return;
                }
                statuses.assign(parcelId, courierId);
            });
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }

    private JsonNode read(String message) {
        if (message == null || message.isBlank()) {
            throw new MalformedEventException("empty message");
        }
        try {
            JsonNode envelope = json.readTree(message);
            if (envelope == null || !envelope.isObject()) {
                throw new MalformedEventException("not an event envelope");
            }
            return envelope;
        } catch (JacksonException notJson) {
            throw new MalformedEventException("not JSON", notJson);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.asString(""));
        } catch (IllegalArgumentException invalid) {
            throw new MalformedEventException("ParcelAssigned has no valid " + field, invalid);
        }
    }
}
