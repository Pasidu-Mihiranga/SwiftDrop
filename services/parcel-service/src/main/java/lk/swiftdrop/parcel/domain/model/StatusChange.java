package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;

/**
 * One row of a parcel's append-only history.
 *
 * @param fromStatus    null for the first entry, when the parcel is created
 * @param failureReason set when the parcel moved to FAILED
 * @param proofId       the uploaded photo or signature a delivery was proven with, if any
 * @param position      where the courier was, when the device shared it
 */
public record StatusChange(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                           String note, Instant occurredAt, FailureReason failureReason, String proofId,
                           GeoPoint position) {

    /** An entry without courier details: booking, cancellation. */
    public StatusChange(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                        String note, Instant occurredAt) {
        this(fromStatus, toStatus, actorId, actorRole, note, occurredAt, null, null, null);
    }
}
