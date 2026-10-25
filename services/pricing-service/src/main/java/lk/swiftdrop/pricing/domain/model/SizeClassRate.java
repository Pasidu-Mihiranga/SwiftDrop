package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * The limits and fee multiplier configured for one size class.
 *
 * @param multiplier applied to the base and weight subtotal; {@code 1} means no size surcharge
 */
public record SizeClassRate(SizeClassCode code, BigDecimal maxWeightKg, String maxDimensionsCm, BigDecimal multiplier) {

    public SizeClassRate {
        if (code == null) {
            throw new IllegalArgumentException("Size class code is required");
        }
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Size class multiplier must be at least 1");
        }
        if (maxWeightKg == null || maxWeightKg.signum() <= 0) {
            throw new IllegalArgumentException("Size class maximum weight must be positive");
        }
    }

    /** Whether a parcel of this weight still fits the class. */
    public boolean accepts(BigDecimal weightKg) {
        return weightKg != null && weightKg.compareTo(maxWeightKg) <= 0;
    }
}
