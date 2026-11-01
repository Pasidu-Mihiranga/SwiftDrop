package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * One band of a lane's tiered weight pricing.
 *
 * @param upToKg    the top of the band; null is the open-ended last band
 * @param perKgFee  charged for each kilogram falling inside this band
 */
public record WeightTier(BigDecimal upToKg, BigDecimal perKgFee) {

    public WeightTier {
        if (upToKg != null && upToKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight tier limit must be positive");
        }
        if (perKgFee == null || perKgFee.signum() < 0) {
            throw new IllegalArgumentException("Weight tier rate must not be negative");
        }
    }

    /** Whether this band has no upper limit, so it absorbs whatever weight is left. */
    public boolean isOpenEnded() {
        return upToKg == null;
    }
}
