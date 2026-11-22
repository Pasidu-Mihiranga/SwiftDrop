package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Applies dispatch assignment events to the parcel aggregate idempotently. */
@Component
public class ParcelAssignmentListener {

    static final String CONSUMER = "parcel-assignment";

    private final ObjectMapper json;
    private final IdempotentConsumer idempotency;
    private final ParcelRepository parcels;
    private final TransactionOperations transaction;

    public ParcelAssignmentListener(ObjectMapper json, IdempotentConsumer idempotency, ParcelRepository parcels,
                                    TransactionOperations transaction) {
        this.json = json;
        this.idempotency = idempotency;
        this.parcels = parcels;
        this.transaction = transaction;
    }

    @KafkaListener(id = "parcel-assignment", idIsGroup = false, topics = Topics.PARCEL_ASSIGNED)
    public void on(ConsumerRecord<String, String> record) throws Exception {
        EventEnvelope<ParcelAssigned> event = json.readValue(record.value(),
                new TypeReference<EventEnvelope<ParcelAssigned>>() { });
        transaction.executeWithoutResult(status -> {
            if (!idempotency.firstDelivery(event.eventId(), CONSUMER)) return;
            var data = event.data();
            var parcel = parcels.findById(data.parcelId()).orElseThrow();
            parcels.save(parcel.assignCourier(data.courierId(), data.assignedAt()));
        });
    }
}
