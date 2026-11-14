package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The parcel aggregate: the consistency boundary for status, fee and history.
 *
 * <p>Immutable. A change returns a new parcel with one more history entry, so the status and its
 * history can never disagree.
 *
 * @param version optimistic-lock version; null until the parcel has been stored
 */
public record Parcel(UUID id, TrackingNumber trackingNumber, ParcelSource source, String customerId,
                     String merchantId, String merchantOrderRef, BookingDetails details, ParcelStatus status,
                     String courierId, List<StatusChange> history, Instant createdAt, Instant updatedAt,
                     Long version) {

    public Parcel {
        history = List.copyOf(history);
    }

    /** A customer's confirmed booking, in status CREATED with its first history entry. */
    public static Parcel bookForCustomer(TrackingNumber trackingNumber, String customerId, BookingDetails details,
                                         Instant now) {
        StatusChange created = new StatusChange(null, ParcelStatus.CREATED, customerId, ActorRole.CUSTOMER, null, now);
        return new Parcel(UUID.randomUUID(), trackingNumber, ParcelSource.CUSTOMER, customerId, null, null,
                details, ParcelStatus.CREATED, null, List.of(created), now, now, null);
    }

    public boolean isCancellable() {
        return status == ParcelStatus.CREATED;
    }

    /**
     * Cancels a parcel that no courier has picked up yet.
     *
     * @throws ParcelNotCancellableException when the parcel has already moved on
     */
    public Parcel cancel(String actorId, ActorRole actorRole, Instant now) {
        if (!isCancellable()) {
            throw new ParcelNotCancellableException();
        }
        List<StatusChange> extended = new ArrayList<>(history);
        extended.add(new StatusChange(status, ParcelStatus.CANCELLED, actorId, actorRole, null, now));
        return new Parcel(id, trackingNumber, source, customerId, merchantId, merchantOrderRef, details,
                ParcelStatus.CANCELLED, courierId, extended, createdAt, now, version);
    }
}
