package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;

/**
 * One row of a parcel's append-only history.
 *
 * @param fromStatus null for the first entry, when the parcel is created
 */
public record StatusChange(ParcelStatus fromStatus, ParcelStatus toStatus, String actorId, ActorRole actorRole,
                           String note, Instant occurredAt) {
}
