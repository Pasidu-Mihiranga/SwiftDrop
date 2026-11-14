package lk.swiftdrop.parcel.infrastructure;

import jakarta.persistence.*;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel")
public class ParcelEntity {
    @Id private UUID id;
    @Column(name = "tracking_number", nullable = false, unique = true) private String trackingNumber;
    @Column(name = "customer_id") private String customerId;
    @Column(name = "merchant_id") private String merchantId;
    @Column(name = "courier_id") private String courierId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ParcelStatus status;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ParcelEntity() { }
    public ParcelEntity(UUID id, String trackingNumber, String customerId, String merchantId) {
        this.id = id; this.trackingNumber = trackingNumber; this.customerId = customerId; this.merchantId = merchantId;
        this.status = ParcelStatus.CREATED; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getCustomerId() { return customerId; }
    public String getMerchantId() { return merchantId; }
    public String getCourierId() { return courierId; }
    public void assignCourier(String courierId) { this.courierId = courierId; this.updatedAt = Instant.now(); }
    public ParcelStatus getStatus() { return status; }
    public void moveTo(ParcelStatus status) { this.status = status; this.updatedAt = Instant.now(); }
}
