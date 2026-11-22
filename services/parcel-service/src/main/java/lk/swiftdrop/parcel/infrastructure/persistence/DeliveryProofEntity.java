package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_proof")
class DeliveryProofEntity {

    @Id
    private UUID id;
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;
    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "used_at")
    private Instant usedAt;

    protected DeliveryProofEntity() {
    }

    DeliveryProofEntity(UUID id, UUID parcelId, String objectKey, String contentType, long sizeBytes, Instant createdAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    DeliveryProofStore.ProofRecord toRecord() {
        return new DeliveryProofStore.ProofRecord(id, parcelId, objectKey, contentType, sizeBytes, createdAt, usedAt);
    }

    void markUsed() {
        usedAt = Instant.now();
    }
}
