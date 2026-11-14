package lk.swiftdrop.parcel.infrastructure;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="delivery_proof")
public class DeliveryProofEntity {
    @Id private UUID id; @Column(name="parcel_id", nullable=false) private UUID parcelId;
    @Column(name="object_key", nullable=false, unique=true) private String objectKey;
    @Column(name="content_type", nullable=false) private String contentType;
    @Column(name="size_bytes", nullable=false) private long sizeBytes;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="used_at") private Instant usedAt;
    protected DeliveryProofEntity() { }
    public DeliveryProofEntity(UUID id, UUID parcelId, String objectKey, String contentType, long sizeBytes) {
        this.id=id; this.parcelId=parcelId; this.objectKey=objectKey; this.contentType=contentType; this.sizeBytes=sizeBytes; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getParcelId(){return parcelId;} public String getObjectKey(){return objectKey;}
    public String getContentType(){return contentType;} public long getSizeBytes(){return sizeBytes;} public Instant getUsedAt(){return usedAt;}
    public void markUsed(){usedAt=Instant.now();}
}
