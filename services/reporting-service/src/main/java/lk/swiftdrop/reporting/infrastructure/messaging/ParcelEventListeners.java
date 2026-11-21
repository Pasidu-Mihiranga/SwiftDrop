package lk.swiftdrop.reporting.infrastructure.messaging;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.reporting.application.command.ProjectionService;
import lk.swiftdrop.reporting.infrastructure.metrics.ReportingMetrics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the three parcel topics into the projection. The consumer group comes from
 * {@code spring.kafka.consumer.group-id}; failures are handled by {@link KafkaConsumerConfiguration}.
 */
@Component
class ParcelEventListeners {

    private final EventReader reader;
    private final ProjectionService projection;
    private final ReportingMetrics metrics;

    ParcelEventListeners(EventReader reader, ProjectionService projection, ReportingMetrics metrics) {
        this.reader = reader;
        this.projection = projection;
        this.metrics = metrics;
    }

    @KafkaListener(id = "reporting-parcel-created", idIsGroup = false, topics = Topics.PARCEL_CREATED)
    void onParcelCreated(String value) {
        EventEnvelope<ParcelCreated> event = reader.read(value, ParcelCreated.class, ParcelCreated.TYPE, ParcelCreated.VERSION);
        metrics.processed(ParcelCreated.TYPE, projection.onParcelCreated(event), event.occurredAt());
    }

    @KafkaListener(id = "reporting-parcel-status", idIsGroup = false, topics = Topics.PARCEL_STATUS_CHANGED)
    void onStatusChanged(String value) {
        EventEnvelope<ParcelStatusChanged> event = reader.read(value, ParcelStatusChanged.class,
                ParcelStatusChanged.TYPE, ParcelStatusChanged.VERSION);
        metrics.processed(ParcelStatusChanged.TYPE, projection.onStatusChanged(event), event.occurredAt());
    }

    @KafkaListener(id = "reporting-parcel-assigned", idIsGroup = false, topics = Topics.PARCEL_ASSIGNED)
    void onParcelAssigned(String value) {
        EventEnvelope<ParcelAssigned> event = reader.read(value, ParcelAssigned.class, ParcelAssigned.TYPE, ParcelAssigned.VERSION);
        metrics.processed(ParcelAssigned.TYPE, projection.onParcelAssigned(event), event.occurredAt());
    }
}
