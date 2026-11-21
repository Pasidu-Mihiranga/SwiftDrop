package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Changes an option's label, fee, order or availability. Its code never changes. */
public record SpecialHandlingUpdateRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal fee,
        @NotNull Boolean active,
        @Min(0) @Max(9999) int sortOrder) {
}
