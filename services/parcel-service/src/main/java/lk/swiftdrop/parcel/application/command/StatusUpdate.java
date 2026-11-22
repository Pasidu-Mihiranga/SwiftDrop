package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.model.GeoPoint;
import lk.swiftdrop.parcel.domain.model.ProofOfDelivery;

/** A courier's (or dispatcher's) status change, as the API received it. */
public record StatusUpdate(ParcelStatus toStatus, String note, FailureReason failureReason, ProofOfDelivery proof,
                           GeoPoint position) {
}
