package lk.swiftdrop.dispatch.infrastructure;

import lk.swiftdrop.dispatch.application.DispatchService;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.parcel.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DispatchListeners {
    private final ObjectMapper mapper;
    private final DispatchService service;
    public DispatchListeners(ObjectMapper mapper, DispatchService service) { this.mapper = mapper; this.service = service; }
    public <T> EventEnvelope<T> read(String json, Class<T> type, String expected) {
        EventEnvelope<T> event = mapper.readValue(json, mapper.getTypeFactory().constructParametricType(EventEnvelope.class, type));
        if (event == null || event.eventId() == null || event.data() == null || event.eventVersion() != 1 || !expected.equals(event.eventType()))
            throw new IllegalArgumentException("Unsupported dispatch event envelope");
        return event;
    }
    @KafkaListener(topics = Topics.PARCEL_CREATED)
    public void created(String json) { service.created(read(json, ParcelCreated.class, ParcelCreated.TYPE)); }
    @KafkaListener(topics = Topics.PARCEL_STATUS_CHANGED)
    public void status(String json) { service.status(read(json, ParcelStatusChanged.class, ParcelStatusChanged.TYPE)); }
}
