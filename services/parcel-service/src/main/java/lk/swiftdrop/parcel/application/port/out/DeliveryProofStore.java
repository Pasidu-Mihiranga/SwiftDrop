package lk.swiftdrop.parcel.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for proof metadata; photo bytes remain in object storage. */
public interface DeliveryProofStore {

    ProofRecord save(UUID proofId, UUID parcelId, String objectKey, String contentType, long sizeBytes);

    Optional<ProofRecord> find(UUID proofId, UUID parcelId);

    ProofRecord markUsed(ProofRecord proof);

    record ProofRecord(UUID id, UUID parcelId, String objectKey, String contentType, long sizeBytes,
                       Instant createdAt, Instant usedAt) {
    }
}
