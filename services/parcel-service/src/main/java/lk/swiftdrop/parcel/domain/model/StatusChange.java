package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;

/**
 * One row of a parcel's append-only history.
 *
 * @param fromStatus null for the first entry, when the parcel is created
 */
public record StatusChange(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                           String note, Instant occurredAt, FailureReason failureReason, String proofType,
                           String proofId, String receivedBy, Location location) {

    public StatusChange(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                        String note, Instant occurredAt) {
        this(fromStatus, toStatus, actorId, actorRole, note, occurredAt, null, null, null, null, null);
    }
}
