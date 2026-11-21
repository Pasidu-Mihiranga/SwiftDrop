package lk.swiftdrop.reporting.support;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds Kafka record values from the shared examples in contracts/events/examples, changing only
 * the fields a test cares about. Tests therefore always send contract-shaped JSON.
 */
public final class ContractEvents {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ContractEvents() {
    }

    public static String created(UUID eventId, UUID parcelId, String dropoffZone, Instant createdAt) {
        ObjectNode envelope = example("parcel-created.v1", eventId, createdAt);
        ObjectNode data = (ObjectNode) envelope.get("data");
        data.put("parcelId", parcelId.toString());
        data.put("createdAt", createdAt.toString());
        ((ObjectNode) data.get("dropoff")).put("zoneCode", dropoffZone);
        return MAPPER.writeValueAsString(envelope);
    }

    public static String statusChanged(UUID eventId, UUID parcelId, String toStatus, String courierId, Instant changedAt) {
        ObjectNode envelope = example("parcel-status-changed.v1", eventId, changedAt);
        ObjectNode data = (ObjectNode) envelope.get("data");
        data.put("parcelId", parcelId.toString());
        data.put("toStatus", toStatus);
        data.put("changedAt", changedAt.toString());
        if (courierId == null) {
            data.putNull("courierId");
        } else {
            data.put("courierId", courierId);
        }
        if ("FAILED".equals(toStatus)) {
            data.put("failureReason", "RECIPIENT_UNAVAILABLE");
        }
        if (!"DELIVERED".equals(toStatus)) {
            data.putNull("proof");
        }
        return MAPPER.writeValueAsString(envelope);
    }

    public static String assigned(UUID eventId, UUID parcelId, String courierId, Instant assignedAt) {
        ObjectNode envelope = example("parcel-assigned.v1", eventId, assignedAt);
        ObjectNode data = (ObjectNode) envelope.get("data");
        data.put("parcelId", parcelId.toString());
        data.put("courierId", courierId);
        data.put("assignedAt", assignedAt.toString());
        return MAPPER.writeValueAsString(envelope);
    }

    /** The unmodified example, as published in the contracts folder. */
    public static String raw(String contract) {
        String path = "/contracts/events/examples/" + contract + ".example.json";
        try (InputStream in = ContractEvents.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path + " on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static ObjectNode example(String contract, UUID eventId, Instant occurredAt) {
        ObjectNode envelope = (ObjectNode) MAPPER.readTree(raw(contract));
        envelope.put("eventId", eventId.toString());
        envelope.put("occurredAt", occurredAt.toString());
        return envelope;
    }
}
