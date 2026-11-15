package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lk.swiftdrop.pricing.domain.model.Dimensions;

import java.math.BigDecimal;

/**
 * Omitted limits keep their current configured values.
 *
 * <p>The geometry is given as three measurements rather than as a label. It used to be a free
 * text string that nothing read, so a class could claim limits the service never enforced.
 */
public record SizeClassUpdateRequest(
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2) BigDecimal maxWeightKg,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 1) BigDecimal maxLengthCm,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 1) BigDecimal maxWidthCm,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 1) BigDecimal maxHeightCm,
        @NotNull @DecimalMin("1") @Digits(integer = 2, fraction = 3) BigDecimal multiplier) {

    @AssertTrue(message = "give all three of maxLengthCm, maxWidthCm and maxHeightCm, or none")
    public boolean isDimensionsAllOrNothing() {
        int given = (maxLengthCm == null ? 0 : 1) + (maxWidthCm == null ? 0 : 1)
                + (maxHeightCm == null ? 0 : 1);
        return given == 0 || given == 3;
    }

    /** Null when the caller gave no geometry, meaning the configured limits stand. */
    public Dimensions toDimensions() {
        return maxLengthCm == null ? null : new Dimensions(maxLengthCm, maxWidthCm, maxHeightCm);
    }
}
