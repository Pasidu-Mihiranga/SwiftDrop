package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class DeliveryProofJpaStore implements DeliveryProofStore {

    private final SpringDataDeliveryProofRepository rows;

    DeliveryProofJpaStore(SpringDataDeliveryProofRepository rows) {
        this.rows = rows;
    }

    @Override
    public ProofRecord save(UUID proofId, UUID parcelId, String objectKey, String contentType, long sizeBytes) {
        return rows.saveAndFlush(new DeliveryProofEntity(proofId, parcelId, objectKey, contentType, sizeBytes,
                Instant.now())).toRecord();
    }

    @Override
    public Optional<ProofRecord> find(UUID proofId, UUID parcelId) {
        return rows.findByIdAndParcelId(proofId, parcelId).map(DeliveryProofEntity::toRecord);
    }

    @Override
    public ProofRecord markUsed(ProofRecord proof) {
        DeliveryProofEntity row = rows.findByIdAndParcelId(proof.id(), proof.parcelId()).orElseThrow();
        row.markUsed();
        return rows.saveAndFlush(row).toRecord();
    }
}
