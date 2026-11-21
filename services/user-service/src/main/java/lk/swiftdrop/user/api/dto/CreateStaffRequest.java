package lk.swiftdrop.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;

import java.util.UUID;

/**
 * A new staff account. Courier details are optional and must be given together.
 *
 * @param role COURIER, DISPATCHER or ADMIN
 */
public record CreateStaffRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^(\\+94|0)7[0-9]{8}$",
                message = "must be a Sri Lankan mobile number such as 0771234567 or +94771234567")
        String phone,
        @NotNull UserRole role,
        VehicleType vehicleType,
        UUID homeZoneId,
        @Min(1) @Max(200) Integer maxDailyCapacity) {

    /** None of the three courier fields, or all of them. */
    public boolean courierDetailsComplete() {
        return vehicleType != null && homeZoneId != null && maxDailyCapacity != null;
    }

    public boolean courierDetailsGiven() {
        return vehicleType != null || homeZoneId != null || maxDailyCapacity != null;
    }
}
