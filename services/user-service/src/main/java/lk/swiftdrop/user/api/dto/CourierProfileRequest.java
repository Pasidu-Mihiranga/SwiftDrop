package lk.swiftdrop.user.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lk.swiftdrop.user.domain.model.VehicleType;

import java.util.UUID;

/**
 * A courier's dispatch details (PUT: every field is replaced).
 *
 * @param homeZoneId pricing-service zone id
 * @param active     omitted means {@code true}
 */
public record CourierProfileRequest(
        @NotNull VehicleType vehicleType,
        @NotNull UUID homeZoneId,
        @NotNull @Min(1) @Max(200) Integer maxDailyCapacity,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
