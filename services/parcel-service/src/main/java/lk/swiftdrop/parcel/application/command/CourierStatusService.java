package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Changes a parcel status and publishes the matching event in one transaction. */
@Service
public class CourierStatusService {

    private final ParcelRepository parcels;
    private final DeliveryProofStore proofs;
    private final OutboxWriter outbox;
    private final Clock clock;

    public CourierStatusService(ParcelRepository parcels, DeliveryProofStore proofs, OutboxWriter outbox, Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Parcel update(UUID parcelId, ParcelStatus target, String note, FailureReason failureReason,
                         ParcelStatusChanged.Proof proof, Location location, AuthenticatedUser caller) {
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canUpdateStatus(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        if (target == ParcelStatus.CANCELLED && !caller.hasRole("ADMIN")) {
            throw new BusinessRuleViolationException("COURIER_CANNOT_CANCEL", "Courier status updates cannot cancel a parcel.");
        }
        validate(target, failureReason, proof);
        if (proof != null && proof.proofId() != null) {
            UUID proofId = parseProofId(proof.proofId());
            DeliveryProofStore.ProofRecord record = proofs.find(proofId, parcelId)
                    .orElseThrow(() -> new BusinessRuleViolationException("PROOF_NOT_FOUND", "The proof is not available."));
            if (record.usedAt() != null) {
                throw new BusinessRuleViolationException("PROOF_ALREADY_USED", "The proof has already been finalized.");
            }
            proofs.markUsed(record);
        }
        ActorRole role = roleOf(caller);
        Parcel changed = parcel.changeStatus(target, caller.id(), role, note, failureReason,
                proof == null || proof.type() == null ? null : proof.type().name(),
                proof == null ? null : proof.proofId(), proof == null ? null : proof.receivedBy(), location,
                clock.instant());
        Parcel saved = parcels.save(changed);
        outbox.publish(new ParcelStatusChanged(saved.id(), saved.trackingNumber().value(), saved.customerId(),
                saved.merchantId(), saved.courierId(), parcel.status(), saved.status(), note, failureReason, proof,
                location, new Actor(caller.id(), role), saved.updatedAt()));
        return saved;
    }

    @Transactional
    public Parcel reattempt(UUID parcelId, String note, AuthenticatedUser caller) {
        if (!caller.isStaff()) {
            throw new ResourceNotFoundException("PARCEL_NOT_FOUND", "The parcel was not found");
        }
        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        Parcel changed = parcel.changeStatus(ParcelStatus.OUT_FOR_DELIVERY, caller.id(), roleOf(caller), note,
                null, null, null, null, null, clock.instant());
        Parcel saved = parcels.save(changed);
        outbox.publish(new ParcelStatusChanged(saved.id(), saved.trackingNumber().value(), saved.customerId(),
                saved.merchantId(), saved.courierId(), parcel.status(), saved.status(), note, null, null, null,
                new Actor(caller.id(), roleOf(caller)), saved.updatedAt()));
        return saved;
    }

    private static void validate(ParcelStatus target, FailureReason failureReason, ParcelStatusChanged.Proof proof) {
        if (target == ParcelStatus.FAILED && failureReason == null) {
            throw new BusinessRuleViolationException("FAILURE_REASON_REQUIRED", "A failure reason is required.");
        }
        if (target != ParcelStatus.DELIVERED) return;
        if (proof == null || proof.type() == null) {
            throw new BusinessRuleViolationException("DELIVERY_PROOF_REQUIRED", "Delivery proof is required.");
        }
        if (proof.type() == ParcelStatusChanged.ProofType.NAME
                && (proof.receivedBy() == null || proof.receivedBy().isBlank())) {
            throw new BusinessRuleViolationException("RECIPIENT_NAME_REQUIRED", "Recipient name is required.");
        }
        if (proof.type() == ParcelStatusChanged.ProofType.PHOTO
                && (proof.proofId() == null || proof.proofId().isBlank())) {
            throw new BusinessRuleViolationException("PHOTO_PROOF_REQUIRED", "Photo proof is required.");
        }
    }

    private static UUID parseProofId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("PROOF_NOT_FOUND", "The proof is not available.");
        }
    }

    private static ActorRole roleOf(AuthenticatedUser caller) {
        if (caller.hasRole("ADMIN")) return ActorRole.ADMIN;
        if (caller.hasRole("DISPATCHER")) return ActorRole.DISPATCHER;
        return ActorRole.COURIER;
    }
}
