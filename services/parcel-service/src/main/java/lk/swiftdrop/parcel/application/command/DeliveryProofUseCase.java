package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore.StoredProof;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Proof-of-delivery photos and signatures (M4). The assigned courier asks for an upload link, the
 * phone uploads the file straight to object storage, and the status update refers to it by
 * {@code proofId}. Keys are random and the bucket is private; anyone who may see the parcel gets a
 * short-lived download link.
 */
@Service
public class DeliveryProofUseCase {

    /** JPEG or PNG, at most 5 MB (the brief's limit). */
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    /** Where to upload one proof file. */
    public record ProofUpload(UUID proofId, String uploadUrl, java.time.Instant expiresAt) { }

    private final ParcelRepository parcels;
    private final DeliveryProofStore proofs;
    private final ProofStorage storage;
    private final Clock clock;

    public DeliveryProofUseCase(ParcelRepository parcels, DeliveryProofStore proofs, ProofStorage storage,
                                Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * @throws ResourceNotFoundException       when the parcel is missing or not assigned to the caller
     * @throws BusinessRuleViolationException  for a file type other than JPEG or PNG, or over 5 MB
     */
    @Transactional
    public ProofUpload requestUpload(AuthenticatedUser caller, UUID parcelId, String contentType, long sizeBytes) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessRuleViolationException("PROOF_TYPE_NOT_ALLOWED", "Upload a JPEG or PNG image.");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_BYTES) {
            throw new BusinessRuleViolationException("PROOF_TOO_LARGE", "The image must be 5 MB or smaller.");
        }
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canUpdateStatus(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        UUID proofId = UUID.randomUUID();
        // A random key per file: nothing about the parcel or the customer can be guessed from it.
        String key = "proofs/" + parcel.id() + "/" + proofId + "." + extension;
        proofs.save(new StoredProof(proofId, parcel.id(), key, contentType, sizeBytes, clock.instant(), null));
        PresignedUrl upload = storage.presignUpload(key, contentType, sizeBytes);
        return new ProofUpload(proofId, upload.url(), upload.expiresAt());
    }

    /**
     * A short-lived link to view a proof.
     *
     * @throws ResourceNotFoundException when the parcel or proof is missing, or the caller may not see the parcel
     */
    @Transactional(readOnly = true)
    public PresignedUrl viewLink(AuthenticatedUser caller, UUID parcelId, UUID proofId) {
        parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canView(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        StoredProof proof = proofs.findById(proofId)
                .filter(stored -> stored.parcelId().equals(parcelId))
                .orElseThrow(() -> ResourceNotFoundException.of("Proof", proofId));
        return storage.presignDownload(proof.objectKey());
    }
}
