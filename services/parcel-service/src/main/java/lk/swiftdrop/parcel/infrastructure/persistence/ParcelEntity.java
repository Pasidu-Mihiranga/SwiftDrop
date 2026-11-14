package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.infrastructure.crypto.EncryptedAddressConverter;
import lk.swiftdrop.parcel.infrastructure.crypto.EncryptedStringConverter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Row of {@code parcel}. Addresses, the recipient phone and the recipient email are encrypted by
 * their converters; everything else is a copied value, never a foreign key into another service.
 */
@Entity
@Table(name = "parcel")
public class ParcelEntity {

    @Id
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 22, updatable = false)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ParcelSource source;

    @Column(name = "customer_id", length = 64, updatable = false)
    private String customerId;

    @Column(name = "merchant_id", length = 64, updatable = false)
    private String merchantId;

    @Column(name = "merchant_order_ref", length = 100, updatable = false)
    private String merchantOrderRef;

    @Convert(converter = EncryptedAddressConverter.class)
    @Column(name = "pickup_address_enc", nullable = false, columnDefinition = "text", updatable = false)
    private Address pickup;

    @Convert(converter = EncryptedAddressConverter.class)
    @Column(name = "dropoff_address_enc", nullable = false, columnDefinition = "text", updatable = false)
    private Address dropoff;

    @Column(name = "pickup_zone", length = 20, updatable = false)
    private String pickupZone;

    @Column(name = "dropoff_zone", length = 20, updatable = false)
    private String dropoffZone;

    @Column(name = "recipient_name", nullable = false, length = 100, updatable = false)
    private String recipientName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "recipient_phone_enc", nullable = false, columnDefinition = "text", updatable = false)
    private String recipientPhone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "recipient_email_enc", columnDefinition = "text", updatable = false)
    private String recipientEmail;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_class", nullable = false, length = 4, updatable = false)
    private SizeClass sizeClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false, length = 10, updatable = false)
    private ServiceLevel serviceLevel;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal fee;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "quote_id", updatable = false)
    private UUID quoteId;

    @Column(length = 300, updatable = false)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelStatus status;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL)
    @OrderBy("occurredAt ASC")
    private List<ParcelStatusHistoryEntity> history = new ArrayList<>();

    protected ParcelEntity() {
        // for JPA
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getTrackingNumber() {
        return trackingNumber;
    }

    void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    ParcelSource getSource() {
        return source;
    }

    void setSource(ParcelSource source) {
        this.source = source;
    }

    String getCustomerId() {
        return customerId;
    }

    void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    String getMerchantId() {
        return merchantId;
    }

    void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    void setMerchantOrderRef(String merchantOrderRef) {
        this.merchantOrderRef = merchantOrderRef;
    }

    Address getPickup() {
        return pickup;
    }

    void setPickup(Address pickup) {
        this.pickup = pickup;
    }

    Address getDropoff() {
        return dropoff;
    }

    void setDropoff(Address dropoff) {
        this.dropoff = dropoff;
    }

    String getPickupZone() {
        return pickupZone;
    }

    void setPickupZone(String pickupZone) {
        this.pickupZone = pickupZone;
    }

    String getDropoffZone() {
        return dropoffZone;
    }

    void setDropoffZone(String dropoffZone) {
        this.dropoffZone = dropoffZone;
    }

    String getRecipientName() {
        return recipientName;
    }

    void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    String getRecipientPhone() {
        return recipientPhone;
    }

    void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    String getRecipientEmail() {
        return recipientEmail;
    }

    void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    BigDecimal getWeightKg() {
        return weightKg;
    }

    void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    SizeClass getSizeClass() {
        return sizeClass;
    }

    void setSizeClass(SizeClass sizeClass) {
        this.sizeClass = sizeClass;
    }

    ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    void setServiceLevel(ServiceLevel serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    BigDecimal getFee() {
        return fee;
    }

    void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    String getCurrency() {
        return currency;
    }

    void setCurrency(String currency) {
        this.currency = currency;
    }

    UUID getQuoteId() {
        return quoteId;
    }

    void setQuoteId(UUID quoteId) {
        this.quoteId = quoteId;
    }

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    ParcelStatus getStatus() {
        return status;
    }

    void setStatus(ParcelStatus status) {
        this.status = status;
    }

    String getCourierId() {
        return courierId;
    }

    void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    Long getVersion() {
        return version;
    }

    List<ParcelStatusHistoryEntity> getHistory() {
        return history;
    }
}
