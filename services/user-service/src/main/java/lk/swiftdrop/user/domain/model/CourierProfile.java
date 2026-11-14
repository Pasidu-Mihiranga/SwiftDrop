package lk.swiftdrop.user.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispatch details of one courier.
 *
 * @param courierId        Keycloak subject of the courier
 * @param vehicleType      what the courier delivers with
 * @param homeZoneId       pricing-service zone the courier usually works in (copied by value)
 * @param maxDailyCapacity parcels the courier can take in a day, 1 to {@value #MAX_CAPACITY}
 * @param active           whether dispatchers may assign new parcels to this courier
 * @param updatedAt        last change; {@code null} before it is stored
 */
public record CourierProfile(String courierId, VehicleType vehicleType, UUID homeZoneId, int maxDailyCapacity,
                             boolean active, Instant updatedAt) {

    public static final int MAX_CAPACITY = 200;

    public CourierProfile {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier id is required");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type is required");
        }
        if (homeZoneId == null) {
            throw new IllegalArgumentException("Home zone is required");
        }
        if (maxDailyCapacity < 1 || maxDailyCapacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Daily capacity must be between 1 and " + MAX_CAPACITY);
        }
    }
}
