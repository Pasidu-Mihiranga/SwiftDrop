package lk.swiftdrop.user.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lk.swiftdrop.user.domain.model.VehicleType;

import java.time.Instant;
import java.util.UUID;

/** Maps {@code courier_profile}, created by {@code V3__create_courier_profile.sql}. */
@Entity
@Table(name = "courier_profile")
class CourierProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "home_zone_id", nullable = false)
    private UUID homeZoneId;

    @Column(name = "max_daily_capacity", nullable = false)
    private int maxDailyCapacity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** {@code null} until the row exists, which is how Spring Data tells an insert from an update. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CourierProfileEntity() {
        // for JPA
    }

    CourierProfileEntity(String userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void apply(VehicleType newVehicleType, UUID newHomeZoneId, int newMaxDailyCapacity, boolean newActive) {
        this.vehicleType = newVehicleType;
        this.homeZoneId = newHomeZoneId;
        this.maxDailyCapacity = newMaxDailyCapacity;
        this.active = newActive;
    }

    String getUserId() {
        return userId;
    }

    VehicleType getVehicleType() {
        return vehicleType;
    }

    UUID getHomeZoneId() {
        return homeZoneId;
    }

    int getMaxDailyCapacity() {
        return maxDailyCapacity;
    }

    boolean isActive() {
        return active;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
