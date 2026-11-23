package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for {@link DeliveryProofStore}. */
@Repository
public class DeliveryProofJpaStore implements DeliveryProofStore {

    private final SpringDataDeliveryProofRepository rows;

    DeliveryProofJpaStore(SpringDataDeliveryProofRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(StoredProof proof) {
        rows.save(new DeliveryProofEntity(proof.id(), proof.parcelId(), proof.objectKey(), proof.contentType(),
                proof.sizeBytes(), proof.createdAt()));
    }

    @Override
    public Optional<StoredProof> findById(UUID id) {
        return rows.findById(id).map(row -> new StoredProof(row.getId(), row.getParcelId(), row.getObjectKey(),
                row.getContentType(), row.getSizeBytes(), row.getCreatedAt(), row.getUsedAt()));
    }

    @Override
    public void markUsed(UUID id, Instant when) {
        rows.findById(id).ifPresent(row -> row.markUsed(when));
    }
}
