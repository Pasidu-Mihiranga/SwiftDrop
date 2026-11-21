package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.security.Roles;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.ProofOfDelivery;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The parcel lifecycle after booking (M4): a courier moves an assigned parcel through pickup,
 * transit and delivery; dispatch re-attempts a failed delivery; dispatch's assignments set the
 * courier. Each change is one transaction: the parcel, its history row and
 * {@code ParcelStatusChanged} through the outbox commit together, and the parcel's version stops
 * two updates from both succeeding.
 */
@Service
public class UpdateParcelStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateParcelStatusUseCase.class);

    private final ParcelRepository parcels;
    private final DeliveryProofStore proofs;
    private final OutboxWriter outbox;
    private final Clock clock;

    public UpdateParcelStatusUseCase(ParcelRepository parcels, DeliveryProofStore proofs, OutboxWriter outbox,
                                     Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * @throws ResourceNotFoundException        when the parcel is missing or not assigned to the caller
     *                                          (a courier cannot tell another courier's parcel from no parcel)
     * @throws InvalidStatusTransitionException when the lifecycle does not allow the step, or a courier
     *                                          tries to re-attempt a failed delivery themselves
     */
    @Transactional
    public Parcel update(AuthenticatedUser caller, UUID parcelId, StatusUpdate update) {
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canUpdateStatus(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        if (parcel.status() == ParcelStatus.FAILED && !ParcelAccessPolicy.canReattempt(caller)) {
            throw new InvalidStatusTransitionException("A failed delivery is re-attempted by dispatch.");
        }
        return apply(caller, parcel, update);
    }

    /**
     * Dispatch sends a failed delivery out again (FAILED to OUT_FOR_DELIVERY).
     *
     * @throws ResourceNotFoundException        when the parcel is missing or the caller is not staff
     * @throws InvalidStatusTransitionException when the parcel has not failed
     */
    @Transactional
    public Parcel reattempt(AuthenticatedUser caller, UUID parcelId, String note) {
        if (!ParcelAccessPolicy.canReattempt(caller)) {
            throw ResourceNotFoundException.of("Parcel", parcelId);
        }
        Parcel parcel = parcels.findById(parcelId).orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        if (parcel.status() != ParcelStatus.FAILED) {
            throw new InvalidStatusTransitionException("Only a failed delivery can be re-attempted.");
        }
        return apply(caller, parcel, new StatusUpdate(ParcelStatus.OUT_FOR_DELIVERY, note, null, null, null));
    }

    /**
     * Records dispatch's assignment from {@code ParcelAssigned}; a reassignment replaces the courier.
     * An unknown parcel is skipped: dispatch only assigns parcels parcel-service created.
     */
    @Transactional
    public void assign(UUID parcelId, String courierId) {
        parcels.findById(parcelId).ifPresentOrElse(
                parcel -> {
                    if (!courierId.equals(parcel.courierId())) {
                        parcels.save(parcel.assignCourier(courierId, clock.instant()));
                    }
                },
                () -> log.warn("ParcelAssigned for unknown parcel {} skipped", parcelId));
    }

    private Parcel apply(AuthenticatedUser caller, Parcel parcel, StatusUpdate update) {
        ProofOfDelivery proof = update.proof();
        UUID storedProofId = proof == null ? null : storedProofId(parcel.id(), proof.proofId());
        ActorRole role = roleOf(caller);
        Instant now = clock.instant();
        Parcel changed = parcels.save(parcel.changeStatus(update.toStatus(), caller.id(), role, update.note(),
                update.failureReason(), proof, update.position(), now));
        if (storedProofId != null) {
            proofs.markUsed(storedProofId, now);
        }
        outbox.publish(new ParcelStatusChanged(changed.id(), changed.trackingNumber().value(), changed.customerId(),
                changed.merchantId(), changed.courierId(), parcel.status(), changed.status(), update.note(),
                changed.status() == ParcelStatus.FAILED ? update.failureReason() : null, eventProof(proof),
                publicLocation(changed), new Actor(caller.id(), role), changed.updatedAt()));
        return changed;
    }

    /** The proof's id when one is given; it must have been uploaded for this parcel. */
    private UUID storedProofId(UUID parcelId, String proofId) {
        if (proofId == null || proofId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(proofId);
        } catch (IllegalArgumentException notAnId) {
            throw proofNotFound();
        }
        return proofs.findById(id).filter(stored -> stored.parcelId().equals(parcelId))
                .map(DeliveryProofStore.StoredProof::id)
                .orElseThrow(UpdateParcelStatusUseCase::proofNotFound);
    }

    private static BusinessRuleViolationException proofNotFound() {
        return new BusinessRuleViolationException("PROOF_NOT_FOUND",
                "That photo or signature was not uploaded for this parcel. Upload it again.");
    }

    private static ParcelStatusChanged.Proof eventProof(ProofOfDelivery proof) {
        if (proof == null || proof.type() == null) {
            return null;
        }
        return new ParcelStatusChanged.Proof(ParcelStatusChanged.ProofType.valueOf(proof.type().name()),
                proof.receivedBy(), proof.proofId());
    }

    /**
     * City-level location for the public timeline: where the parcel was collected for a pickup, the
     * destination town from out-for-delivery on. Street addresses never go on Kafka.
     */
    private static Location publicLocation(Parcel parcel) {
        boolean atPickup = parcel.status() == ParcelStatus.PICKED_UP;
        boolean atDestination = parcel.status() == ParcelStatus.OUT_FOR_DELIVERY
                || parcel.status() == ParcelStatus.DELIVERED || parcel.status() == ParcelStatus.FAILED;
        if (!atPickup && !atDestination) {
            return null;
        }
        Address address = atPickup ? parcel.details().pickup() : parcel.details().dropoff();
        String zone = atPickup ? parcel.details().pickupZone() : parcel.details().dropoffZone();
        return new Location(address.city(), address.district(), zone);
    }

    private static ActorRole roleOf(AuthenticatedUser caller) {
        if (caller.hasRole(Roles.ADMIN)) {
            return ActorRole.ADMIN;
        }
        if (caller.hasRole(Roles.DISPATCHER)) {
            return ActorRole.DISPATCHER;
        }
        return ActorRole.COURIER;
    }
}
