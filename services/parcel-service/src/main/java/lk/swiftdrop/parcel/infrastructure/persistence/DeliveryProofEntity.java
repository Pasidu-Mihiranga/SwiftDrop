package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Row of {@code delivery_proof}: one photo or signature file in object storage. */
@Entity
@Table(name = "delivery_proof")
public class DeliveryProofEntity {

    @Id
    private UUID id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "object_key", nullable = false, length = 512, updatable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 64, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected DeliveryProofEntity() {
        // for JPA
    }

    DeliveryProofEntity(UUID id, UUID parcelId, String objectKey, String contentType, long sizeBytes,
                        Instant createdAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getParcelId() {
        return parcelId;
    }

    String getObjectKey() {
        return objectKey;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUsedAt() {
        return usedAt;
    }

    void markUsed(Instant when) {
        if (usedAt == null) {
            usedAt = when;
        }
    }
}
