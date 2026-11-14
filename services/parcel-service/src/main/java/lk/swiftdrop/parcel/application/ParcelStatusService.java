package lk.swiftdrop.parcel.application;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import lk.swiftdrop.parcel.domain.state.ParcelStates;
import lk.swiftdrop.parcel.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
public class ParcelStatusService {
    private final ParcelRepository parcels; private final ParcelStatusHistoryRepository history; private final DeliveryProofRepository proofs;
    private final ParcelAccessPolicy access; private final OutboxWriter outbox;
    public ParcelStatusService(ParcelRepository parcels, ParcelStatusHistoryRepository history, DeliveryProofRepository proofs,
                               ParcelAccessPolicy access, OutboxWriter outbox) { this.parcels=parcels; this.history=history; this.proofs=proofs; this.access=access; this.outbox=outbox; }

    @Transactional
    public ParcelStatusChanged update(UUID id, Types.ParcelStatus target, String note, Types.FailureReason reason,
                                      ParcelStatusChanged.Proof proof, Types.Location location, AuthenticatedUser user) {
        ParcelEntity parcel=parcels.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Parcel", id)); access.assertCanUpdate(parcel,user);
        if (target == Types.ParcelStatus.FAILED && reason == null) throw new BusinessRuleViolationException("FAILURE_REASON_REQUIRED", "A failure reason is required.");
        validateProof(target, proof);
        var from=parcel.getStatus(); ParcelStates.of(from).transitionTo(target);
        if (proof != null && proof.proofId() != null) {
            UUID proofId;
            try { proofId = UUID.fromString(proof.proofId()); } catch (IllegalArgumentException ex) { throw new BusinessRuleViolationException("PROOF_NOT_FOUND", "Proof of delivery is not available."); }
            var entity=proofs.findById(proofId).filter(p -> p.getParcelId().equals(id)).orElseThrow(() -> new BusinessRuleViolationException("PROOF_NOT_FOUND", "Proof of delivery is not available.")); entity.markUsed();
        }
        parcel.moveTo(target); Instant now=Instant.now(); String role=user.roles().stream().findFirst().orElse("SYSTEM");
        history.save(new ParcelStatusHistoryEntity(id,from,target,note,reason,proof,location,user.id(),role,now));
        var event=new ParcelStatusChanged(id,parcel.getTrackingNumber(),parcel.getCustomerId(),parcel.getMerchantId(),parcel.getCourierId(),from,target,note,reason,proof,location,new Types.Actor(user.id(), roleOf(user)),now);
        outbox.publish(event); return event;
    }
    private Types.ActorRole roleOf(AuthenticatedUser user) { if (user.hasRole("ADMIN")) return Types.ActorRole.ADMIN; if (user.hasRole("DISPATCHER")) return Types.ActorRole.DISPATCHER; return Types.ActorRole.COURIER; }
    private void validateProof(Types.ParcelStatus target, ParcelStatusChanged.Proof proof) {
        if (target != Types.ParcelStatus.DELIVERED) return;
        if (proof == null || proof.type() == null) throw new BusinessRuleViolationException("DELIVERY_PROOF_REQUIRED", "Proof of delivery is required.");
        if (proof.type() == ParcelStatusChanged.ProofType.NAME && (proof.receivedBy() == null || proof.receivedBy().isBlank())) throw new BusinessRuleViolationException("RECIPIENT_NAME_REQUIRED", "Recipient name is required.");
        if (proof.type() == ParcelStatusChanged.ProofType.PHOTO && (proof.proofId() == null || proof.proofId().isBlank())) throw new BusinessRuleViolationException("PHOTO_PROOF_REQUIRED", "Photo proof is required.");
    }
    @Transactional public void assign(UUID id, String courierId) { parcels.findById(id).ifPresent(p -> p.assignCourier(courierId)); }

    @Transactional(readOnly = true)
    public List<ParcelEntity> assignedTo(AuthenticatedUser user) {
        return user.isStaff() ? parcels.findAll() : parcels.findAllByCourierId(user.id());
    }

    @Transactional
    public ParcelStatusChanged reattempt(UUID id, String note, AuthenticatedUser user) {
        if (!user.isStaff()) throw new BusinessRuleViolationException("STAFF_ONLY", "Only dispatch staff may re-attempt a delivery.");
        return update(id, Types.ParcelStatus.OUT_FOR_DELIVERY, note, null, null, null, user);
    }
}
