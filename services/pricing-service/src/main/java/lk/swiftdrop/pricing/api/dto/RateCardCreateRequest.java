package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Bean Validation mirrors the database precision so bad input fails as 400, not on insert. */
public record RateCardCreateRequest(
        @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String fromZone,

        @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String toZone,

        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal perKgFee,
        @NotNull @DecimalMin("1") @Digits(integer = 2, fraction = 3) BigDecimal expressMultiplier,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal codFeePct,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
