package lk.swiftdrop.merchant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import lk.swiftdrop.merchant.domain.model.PickupAddress;
import lk.swiftdrop.merchant.infrastructure.crypto.EncryptedPickupAddressConverter;
import lk.swiftdrop.merchant.infrastructure.crypto.EncryptedStringConverter;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code merchant}. The contact phone and pickup address are encrypted by their converters.
 * What the applicant stated never changes after insert; only the lifecycle columns are updatable.
 */
@Entity
@Table(name = "merchant")
public class MerchantEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, length = 64, updatable = false)
    private String ownerUserId;

    @Column(name = "business_name", nullable = false, length = 150, updatable = false)
    private String businessName;

    @Column(name = "br_number", nullable = false, length = 30, updatable = false)
    private String brNumber;

    @Column(name = "contact_email", nullable = false, length = 254, updatable = false)
    private String contactEmail;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc", nullable = false, columnDefinition = "text", updatable = false)
    private String contactPhone;

    @Column(name = "website", length = 255, updatable = false)
    private String website;

    @Convert(converter = EncryptedPickupAddressConverter.class)
    @Column(name = "pickup_address_enc", nullable = false, columnDefinition = "text", updatable = false)
    private PickupAddress pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MerchantStatus status;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getBrNumber() { return brNumber; }
    public void setBrNumber(String brNumber) { this.brNumber = brNumber; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public PickupAddress getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(PickupAddress pickupAddress) { this.pickupAddress = pickupAddress; }
    public MerchantStatus getStatus() { return status; }
    public void setStatus(MerchantStatus status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
}
