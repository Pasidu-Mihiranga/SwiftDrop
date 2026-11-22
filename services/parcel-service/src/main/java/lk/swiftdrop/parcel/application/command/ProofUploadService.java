package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Authorizes and records a private presigned proof upload. */
@Service
public class ProofUploadService {

    public static final long MAX_BYTES = 5 * 1024 * 1024L;

    private final ParcelRepository parcels;
    private final DeliveryProofStore proofs;
    private final ProofStorage storage;
    public ProofUploadService(ParcelRepository parcels, DeliveryProofStore proofs, ProofStorage storage) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.storage = storage;
    }

    @Transactional
    public Upload create(UUID parcelId, String contentType, long sizeBytes, AuthenticatedUser caller) {
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canUpdateStatus(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        validate(contentType, sizeBytes);
        UUID proofId = UUID.randomUUID();
        String objectKey = storage.objectKey(parcel.id(), proofId);
        ProofStorage.Upload upload = storage.uploadUrl(objectKey, contentType, sizeBytes);
        proofs.save(proofId, parcel.id(), objectKey, contentType, sizeBytes);
        return new Upload(proofId, upload.url(), upload.expiresAt());
    }

    public ProofStorage.Download download(UUID parcelId, UUID proofId, AuthenticatedUser caller) {
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canView(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        DeliveryProofStore.ProofRecord proof = proofs.find(proofId, parcel.id())
                .orElseThrow(() -> ResourceNotFoundException.of("Proof", proofId));
        return storage.downloadUrl(proof.objectKey());
    }

    private static void validate(String contentType, long sizeBytes) {
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new BusinessRuleViolationException("INVALID_PROOF_TYPE", "Only JPEG and PNG proof images are supported.");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_BYTES) {
            throw new BusinessRuleViolationException("INVALID_PROOF_SIZE", "Proof image must be between 1 byte and 5 MB.");
        }
    }

    public record Upload(UUID proofId, String uploadUrl, Instant expiresAt) { }
}
