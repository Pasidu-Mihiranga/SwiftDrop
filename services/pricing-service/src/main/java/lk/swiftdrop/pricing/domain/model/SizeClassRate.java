package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * The limits and fee multiplier configured for one size class.
 *
 * @param maxDimensions the largest parcel the class takes; compared rotation tolerantly
 * @param multiplier    applied to the base and weight subtotal; {@code 1} means no size surcharge
 */
public record SizeClassRate(SizeClassCode code, BigDecimal maxWeightKg, Dimensions maxDimensions,
                            BigDecimal multiplier) {

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
        if (maxDimensions == null) {
            throw new IllegalArgumentException("Size class maximum dimensions are required");
        }
    }

    /** Whether a parcel of this weight still fits the class. */
    public boolean accepts(BigDecimal weightKg) {
        return weightKg != null && weightKg.compareTo(maxWeightKg) <= 0;
    }

    /**
     * Whether a parcel of these measurements still fits the class.
     *
     * <p>Named rather than overloading {@code accepts} because both arguments are nullable and
     * {@code accepts(null)} would not say which limit it meant.
     */
    public boolean acceptsDimensions(Dimensions parcel) {
        return parcel != null && parcel.fitsWithin(maxDimensions);
    }

    /** The published display form of the geometry, for example {@code 40x30x20}. */
    public String maxDimensionsLabel() {
        return maxDimensions.label();
    }
}
