package lk.swiftdrop.events.dispatch;

import lk.swiftdrop.events.EventPayload;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Schema: contracts/events/parcel-assigned.v1.schema.json */
public record ParcelAssigned(UUID assignmentId, UUID parcelId, String trackingNumber, String courierId,
                             String previousCourierId, LocalDate runDate, Actor assignedBy, Instant assignedAt)
        implements EventPayload {

    public static final String TYPE = "ParcelAssigned";
    public static final int VERSION = 1;

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public int eventVersion() {
        return VERSION;
    }

    @Override
    public String topic() {
        return Topics.PARCEL_ASSIGNED;
    }

    /** Keyed by parcel so assignment and status events of one parcel share ordering semantics. */
    @Override
    public String partitionKey() {
        return parcelId.toString();
    }
}
