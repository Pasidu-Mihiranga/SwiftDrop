package lk.swiftdrop.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.swiftdrop.user.infrastructure.crypto.EncryptedStringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** Maps {@code address}, created by {@code V4__create_address.sql}. Street lines are encrypted in the columns. */
@Entity
@Table(name = "address")
class AddressEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "line1_enc", nullable = false, length = 512)
    private String line1;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "line2_enc", length = 512)
    private String line2;

    @Column(name = "city", nullable = false, length = 60)
    private String city;

    @Column(name = "district", nullable = false, length = 40)
    private String district;

    @Column(name = "postal_code", length = 5)
    private String postalCode;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code null} until the row exists, which is how Spring Data tells an insert from an update. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AddressEntity() {
        // for JPA
    }

    AddressEntity(UUID id, String userId) {
        this.id = id;
        this.userId = userId;
    }

    @PrePersist
    void created() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void touched() {
        updatedAt = Instant.now();
    }

    void apply(String newLabel, String newLine1, String newLine2, String newCity, String newDistrict,
               String newPostalCode, Double newLat, Double newLng, boolean newDefault) {
        this.label = newLabel;
        this.line1 = newLine1;
        this.line2 = newLine2;
        this.city = newCity;
        this.district = newDistrict;
        this.postalCode = newPostalCode;
        this.lat = coordinate(newLat);
        this.lng = coordinate(newLng);
        this.isDefault = newDefault;
    }

    private static BigDecimal coordinate(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    UUID getId() {
        return id;
    }

    String getUserId() {
        return userId;
    }

    String getLabel() {
        return label;
    }

    String getLine1() {
        return line1;
    }

    String getLine2() {
        return line2;
    }

    String getCity() {
        return city;
    }

    String getDistrict() {
        return district;
    }

    String getPostalCode() {
        return postalCode;
    }

    Double getLat() {
        return lat == null ? null : lat.doubleValue();
    }

    Double getLng() {
        return lng == null ? null : lng.doubleValue();
    }

    boolean isDefault() {
        return isDefault;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
