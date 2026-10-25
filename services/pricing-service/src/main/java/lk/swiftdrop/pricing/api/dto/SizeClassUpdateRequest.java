package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Omitted limits keep their current configured values. */
public record SizeClassUpdateRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2) BigDecimal maxWeightKg,
        @Size(max = 32) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String maxDimensionsCm,
        @NotNull @DecimalMin("1") @Digits(integer = 2, fraction = 3) BigDecimal multiplier) {
}
