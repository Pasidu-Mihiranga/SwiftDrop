package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A new handling option. It is offered on new quotes as soon as it is added. */
public record SpecialHandlingCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,31}$") String code,
        @NotBlank @Size(max = 100) String label,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal fee,
        @Min(0) @Max(9999) int sortOrder) {
}
