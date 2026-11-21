package lk.swiftdrop.parcel.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The record of each proof a courier asked to upload, so a status update can refer to it by id. */
public interface DeliveryProofStore {

    /** One uploaded (or announced) proof file. */
    record StoredProof(UUID id, UUID parcelId, String objectKey, String contentType, long sizeBytes,
                       Instant createdAt, Instant usedAt) { }

    void save(StoredProof proof);

    Optional<StoredProof> findById(UUID id);

    /** Marks the proof as the one a status change was proven with. */
    void markUsed(UUID id, Instant when);
}
