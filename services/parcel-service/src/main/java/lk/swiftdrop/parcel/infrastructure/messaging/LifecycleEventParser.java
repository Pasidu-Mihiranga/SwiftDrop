package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the envelope merchant-service's outbox relay publishes on swiftdrop.merchant.lifecycle.v1
 * (contracts/events/envelope.schema.json with a MerchantLifecycleChanged payload,
 * contracts/events/merchant-lifecycle.v1.schema.json).
 *
 * <p>Another event type or version is skipped (empty), as every SwiftDrop consumer does, so the
 * producer can evolve. A message that is not that envelope, or lacks a field the status cache
 * needs, is a {@link MalformedEventException}.
 */
public class LifecycleEventParser {

    /** The parts of one lifecycle event that parcel-service keeps. */
    public record LifecycleEvent(UUID eventId, MerchantStanding standing) { }

    private final ObjectMapper json;

    public LifecycleEventParser(ObjectMapper json) {
        this.json = json;
    }

    public Optional<LifecycleEvent> parse(String message) {
        if (message == null || message.isBlank()) {
            throw new MalformedEventException("empty message");
        }
        JsonNode envelope;
        try {
            envelope = json.readTree(message);
        } catch (JacksonException notJson) {
            throw new MalformedEventException("not JSON", notJson);
        }
        if (envelope == null || !envelope.isObject()) {
            throw new MalformedEventException("not an event envelope");
        }
        if (!MerchantLifecycleChanged.TYPE.equals(text(envelope, "eventType"))
                || envelope.path("eventVersion").asInt(-1) != MerchantLifecycleChanged.VERSION) {
            return Optional.empty();
        }
        UUID eventId = uuid(envelope, "eventId");
        MerchantLifecycleChanged data = payload(envelope.get("data"));
        if (data.merchantId() == null || data.status() == null || data.changedAt() == null
                || data.businessName() == null || data.businessName().isBlank()) {
            throw new MalformedEventException("MerchantLifecycleChanged " + eventId
                    + " lacks merchantId, status, businessName or changedAt");
        }
        MerchantStanding standing = new MerchantStanding(data.merchantId().toString(),
                MerchantStatus.valueOf(data.status().name()), data.businessName().strip(),
                data.contactEmail() == null || data.contactEmail().isBlank() ? null : data.contactEmail().strip(),
                data.changedAt());
        return Optional.of(new LifecycleEvent(eventId, standing));
    }

    private MerchantLifecycleChanged payload(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new MalformedEventException("envelope without data");
        }
        try {
            return json.treeToValue(data, MerchantLifecycleChanged.class);
        } catch (JacksonException unreadable) {
            throw new MalformedEventException("unreadable MerchantLifecycleChanged", unreadable);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new MalformedEventException("envelope without a valid " + field);
        }
    }
}
