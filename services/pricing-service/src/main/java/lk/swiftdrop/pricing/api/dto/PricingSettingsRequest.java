package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fee-wide settings published from a date. Percentages are fractions, so 0.18 is 18 per cent.
 *
 * @param sameDayCutoff local time from which same-day is refused for today; null for none
 */
public record PricingSettingsRequest(
        @NotNull @DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1, fraction = 4)
        BigDecimal fuelSurchargePct,
        @NotNull @DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1, fraction = 4)
        BigDecimal taxPct,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minimumCharge,
        LocalTime sameDayCutoff,
        @NotNull LocalDate effectiveFrom) {
}
