package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.MonetaryRounding;
import lk.swiftdrop.pricing.domain.model.WeightBasis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides what a parcel is billed as.
 *
 * <p>A van runs out of room long before it runs out of payload, so a parcel that displaces
 * more than it weighs is charged on the space it occupies. Volumetric weight is the parcel's
 * volume divided by the configured divisor, and the chargeable weight is whichever of the two
 * measures is greater.
 *
 * <p>This is a separate step from billing part kilograms as whole ones. Substituting volume
 * for mass is a tariff decision; rounding up to the next kilogram is how the weight rule
 * charges. Keeping them apart means a quote can report 4.8 kg volumetric against 3 kg actual
 * and still be charged for 5 kg, which is the explanation a customer actually wants.
 */
public final class ChargeableWeightPolicy {

    private ChargeableWeightPolicy() {
    }

    /**
     * @param dimensions null when the customer gave none
     * @param divisor    null when no divisor is in force
     */
    public static ChargeableWeight of(BigDecimal actualKg, Dimensions dimensions, BigDecimal divisor) {
        if (actualKg == null || actualKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        if (dimensions == null || divisor == null || divisor.signum() <= 0) {
            return ChargeableWeight.actual(actualKg);
        }
        BigDecimal volumetricKg = dimensions.volumeCm3()
                .divide(divisor, MonetaryRounding.SCALE, RoundingMode.HALF_UP);
        return volumetricKg.compareTo(actualKg) > 0
                ? new ChargeableWeight(actualKg, volumetricKg, volumetricKg, WeightBasis.VOLUMETRIC)
                : new ChargeableWeight(actualKg, volumetricKg, actualKg, WeightBasis.ACTUAL);
    }
}
