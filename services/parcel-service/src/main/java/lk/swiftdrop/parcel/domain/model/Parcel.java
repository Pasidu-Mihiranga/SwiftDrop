package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.exception.DeliveryProofRequiredException;
import lk.swiftdrop.parcel.domain.exception.FailureReasonRequiredException;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;
import lk.swiftdrop.parcel.domain.state.ParcelStates;

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

    /**
     * A courier or dispatcher moves the parcel to its next status (M4). The lifecycle decides which
     * steps exist; a failure needs a reason and a delivery needs complete proof. Cancelling has its
     * own rules and goes through {@link #cancel}.
     *
     * @throws InvalidStatusTransitionException when the lifecycle does not allow the step
     * @throws FailureReasonRequiredException   when FAILED comes without a reason
     * @throws DeliveryProofRequiredException   when DELIVERED comes without complete proof
     */
    public Parcel changeStatus(ParcelStatus target, String actorId, ActorRole actorRole, String note,
                               FailureReason failureReason, ProofOfDelivery proof, GeoPoint position, Instant now) {
        if (target == ParcelStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("A parcel is cancelled by its customer, not through a status update.");
        }
        ParcelStates.of(status).transitionTo(target);
        if (target == ParcelStatus.FAILED && failureReason == null) {
            throw new FailureReasonRequiredException();
        }
        if (target == ParcelStatus.DELIVERED) {
            if (proof == null || proof.type() == null) {
                throw new DeliveryProofRequiredException("A delivery needs proof: the recipient's name, or a photo or signature.");
            }
            if (!proof.isComplete()) {
                throw new DeliveryProofRequiredException(proof.type() == ProofOfDelivery.Type.NAME
                        ? "Enter the name of the person who received the parcel."
                        : "Upload the " + proof.type().name().toLowerCase() + " first.");
            }
        }
        List<StatusChange> extended = new ArrayList<>(history);
        extended.add(new StatusChange(status, target, actorId, actorRole, note, now,
                target == ParcelStatus.FAILED ? failureReason : null,
                proof == null ? null : proof.proofId(), position));
        return new Parcel(id, trackingNumber, source, customerId, merchantId, merchantOrderRef, details,
                target, courierId, extended, createdAt, now, version);
    }

    /** Dispatch assigned (or reassigned) the parcel to a courier. Its status does not change. */
    public Parcel assignCourier(String newCourierId, Instant now) {
        if (newCourierId == null || newCourierId.isBlank()) {
            throw new IllegalArgumentException("An assignment needs the courier id");
        }
        return new Parcel(id, trackingNumber, source, customerId, merchantId, merchantOrderRef, details,
                status, newCourierId, history, createdAt, now, version);
    }
}
