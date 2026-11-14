package lk.swiftdrop.parcel.application;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.parcel.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ProofService {
    public static final long MAX_BYTES = 5 * 1024 * 1024L;
    private final ParcelRepository parcels; private final DeliveryProofRepository proofs; private final S3ProofStorage storage;
    public ProofService(ParcelRepository parcels, DeliveryProofRepository proofs, S3ProofStorage storage) { this.parcels=parcels; this.proofs=proofs; this.storage=storage; }
    @Transactional
    public ProofUpload create(UUID parcelId, String contentType, long sizeBytes, AuthenticatedUser user) {
        var parcel=parcels.findById(parcelId).orElseThrow(() -> ResourceNotFoundException.of("Parcel",parcelId)); assertOwner(parcel,user);
        UUID proofId=UUID.randomUUID(); String key=storage.newObjectKey(parcelId,proofId); proofs.save(new DeliveryProofEntity(proofId,parcelId,key,contentType,sizeBytes));
        return new ProofUpload(proofId,storage.uploadUrl(key,contentType),contentType,MAX_BYTES);
    }
    public String download(UUID parcelId, UUID proofId, AuthenticatedUser user) {
        var parcel=parcels.findById(parcelId).orElseThrow(() -> ResourceNotFoundException.of("Parcel",parcelId));
        if (!user.isStaff() && !user.id().equals(parcel.getCourierId()) && !user.id().equals(parcel.getCustomerId())) throw ResourceNotFoundException.of("Parcel",parcelId);
        var proof=proofs.findById(proofId).filter(p -> p.getParcelId().equals(parcelId)).orElseThrow(() -> ResourceNotFoundException.of("Proof",proofId)); return storage.downloadUrl(proof.getObjectKey());
    }
    private void assertOwner(ParcelEntity parcel, AuthenticatedUser user) { if (!user.isStaff() && (!user.hasRole("COURIER") || !user.id().equals(parcel.getCourierId()))) throw ResourceNotFoundException.of("Parcel",parcel.getId()); }
    public record ProofUpload(UUID proofId, String uploadUrl, String contentType, long maxBytes) { }
}
