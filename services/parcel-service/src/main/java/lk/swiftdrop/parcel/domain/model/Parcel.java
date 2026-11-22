package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
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

    /** Same limit as the parcel.merchant_order_ref column and the merchant-order contract. */
    public static final int MAX_ORDER_REF_LENGTH = 100;

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

    /**
     * An order placed by a merchant's system through the merchant API, in status CREATED. The
     * merchant comes from the caller's token; {@code merchantOrderRef} is the merchant's own order
     * number. The first history entry names the merchant's API client as the actor.
     *
     * @throws IllegalArgumentException when the merchant id or order reference is blank, or the
     *                                  reference is longer than {@value #MAX_ORDER_REF_LENGTH}
     */
    public static Parcel bookForMerchant(TrackingNumber trackingNumber, String merchantId, String merchantOrderRef,
                                         String apiClientId, BookingDetails details, Instant now) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("A merchant order needs the merchant id");
        }
        if (merchantOrderRef == null || merchantOrderRef.isBlank() || merchantOrderRef.length() > MAX_ORDER_REF_LENGTH) {
            throw new IllegalArgumentException("A merchant order needs a reference of 1 to " + MAX_ORDER_REF_LENGTH
                    + " characters");
        }
        StatusChange created = new StatusChange(null, ParcelStatus.CREATED, apiClientId, ActorRole.MERCHANT_API, null,
                now);
        return new Parcel(UUID.randomUUID(), trackingNumber, ParcelSource.MERCHANT, null, merchantId,
                merchantOrderRef, details, ParcelStatus.CREATED, null, List.of(created), now, now, null);
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

    public Parcel changeStatus(ParcelStatus target, String actorId, ActorRole actorRole, String note,
                               FailureReason failureReason, String proofType, String proofId, String receivedBy,
                               Location location, Instant now) {
        ParcelStatusTransitions.validate(status, target);
        if (target == ParcelStatus.FAILED && failureReason == null) {
            throw new IllegalArgumentException("A failure reason is required");
        }
        if (target == ParcelStatus.DELIVERED && proofType == null) {
            throw new IllegalArgumentException("Delivery proof is required");
        }
        List<StatusChange> extended = new ArrayList<>(history);
        extended.add(new StatusChange(status, target, actorId, actorRole, note, now, failureReason,
                proofType, proofId, receivedBy, location));
        return new Parcel(id, trackingNumber, source, customerId, merchantId, merchantOrderRef, details,
                target, courierId, extended, createdAt, now, version);
    }

    public Parcel assignCourier(String assignedCourierId, Instant now) {
        return new Parcel(id, trackingNumber, source, customerId, merchantId, merchantOrderRef, details,
                status, assignedCourierId, history, createdAt, now, version);
    }
}
