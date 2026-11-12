package lk.swiftdrop.reporting.infrastructure.messaging;

import lk.swiftdrop.events.EventEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a Kafka record value into a typed envelope and checks that this service understands it.
 * Unknown fields are ignored, so producers can add optional fields without breaking us; a new
 * eventVersion means a breaking change and is rejected rather than half-applied.
 */
@Component
class EventReader {

    private final ObjectMapper mapper;

    EventReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    <T> EventEnvelope<T> read(String json, Class<T> payloadType, String expectedType, int expectedVersion) {
        EventEnvelope<T> event;
        try {
            JavaType type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
            event = mapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new MalformedEventException("Record is not a valid " + expectedType + " envelope", ex);
        }
        if (event == null || event.eventId() == null || event.data() == null) {
            throw new MalformedEventException("Envelope without eventId or data");
        }
        if (!expectedType.equals(event.eventType())) {
            throw new MalformedEventException("Expected " + expectedType + " but got " + event.eventType());
        }
        if (event.eventVersion() != expectedVersion) {
            throw new MalformedEventException("Unsupported " + expectedType + " version " + event.eventVersion());
        }
        return event;
    }
}
