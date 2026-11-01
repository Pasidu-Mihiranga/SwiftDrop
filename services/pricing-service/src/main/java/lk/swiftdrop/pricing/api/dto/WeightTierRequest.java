package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One weight band of a rate card.
 *
 * @param upToKg the top of the band in kilograms; null for the open-ended last band
 */
public record WeightTierRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2) BigDecimal upToKg,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal perKgFee) {
}
