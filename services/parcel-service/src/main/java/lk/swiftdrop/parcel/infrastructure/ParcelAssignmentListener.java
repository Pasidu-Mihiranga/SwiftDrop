package lk.swiftdrop.parcel.infrastructure;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.parcel.application.ParcelStatusService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ParcelAssignmentListener {
    private final ObjectMapper mapper; private final IdempotentConsumer idempotency; private final ParcelStatusService statuses;
    public ParcelAssignmentListener(ObjectMapper mapper, IdempotentConsumer idempotency, ParcelStatusService statuses) { this.mapper=mapper; this.idempotency=idempotency; this.statuses=statuses; }
    @KafkaListener(topics=Topics.PARCEL_ASSIGNED, groupId="parcel-service") @Transactional
    public void on(String value) throws Exception {
        EventEnvelope<ParcelAssigned> event=mapper.readValue(value, new TypeReference<EventEnvelope<ParcelAssigned>>() { });
        if (idempotency.firstDelivery(event.eventId(), "parcel-service-assignment")) statuses.assign(event.data().parcelId(),event.data().courierId());
    }
}
