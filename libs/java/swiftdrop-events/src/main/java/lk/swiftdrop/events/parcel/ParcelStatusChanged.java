package lk.swiftdrop.events.parcel;

import lk.swiftdrop.events.EventPayload;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;
import java.util.UUID;

/** Schema: contracts/events/parcel-status-changed.v1.schema.json */
public record ParcelStatusChanged(UUID parcelId, String trackingNumber, String customerId, String merchantId,
                                  String courierId, ParcelStatus fromStatus, ParcelStatus toStatus, String note,
                                  FailureReason failureReason, Proof proof, Location location,
                                  Actor changedBy, Instant changedAt)
        implements EventPayload {

    public static final String TYPE = "ParcelStatusChanged";
    public static final int VERSION = 1;

    public enum ProofType { NAME, PHOTO, SIGNATURE }

    /** Proof of delivery summary; the photo itself stays in object storage. */
    public record Proof(ProofType type, String receivedBy, String proofId) { }

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
        return Topics.PARCEL_STATUS_CHANGED;
    }

    @Override
    public String partitionKey() {
        return parcelId.toString();
    }
}
