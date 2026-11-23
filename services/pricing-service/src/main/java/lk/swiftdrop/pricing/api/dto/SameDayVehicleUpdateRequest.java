package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A vehicle's same-day limits. The weight limit cannot exceed the 100 kg a quote accepts, which
 * would promise a capacity no quote could use.
 */
public record SameDayVehicleUpdateRequest(
        @NotBlank @Size(max = 60) String label,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 4, fraction = 2)
        BigDecimal maxWeightKg,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2)
        BigDecimal maxDistanceKm,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99") @Digits(integer = 2, fraction = 3)
        BigDecimal rateMultiplier,
        @NotNull Boolean active) {
}
