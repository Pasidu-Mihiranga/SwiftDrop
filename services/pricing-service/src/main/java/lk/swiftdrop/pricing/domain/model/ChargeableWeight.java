package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a parcel is billed as, and why.
 *
 * <p>Both measures are kept, not just the winner, so a customer asking why a light parcel cost
 * what it did can be shown the volumetric figure that beat it.
 *
 * @param volumetricKg null when no dimensions were given, or no divisor is in force
 */
public record ChargeableWeight(BigDecimal actualKg, BigDecimal volumetricKg,
                               BigDecimal chargeableKg, WeightBasis basis) {

    public ChargeableWeight {
        Objects.requireNonNull(actualKg, "actualKg");
        Objects.requireNonNull(chargeableKg, "chargeableKg");
        Objects.requireNonNull(basis, "basis");
    }

    /** A parcel billed on what it weighs. */
    public static ChargeableWeight actual(BigDecimal actualKg) {
        return new ChargeableWeight(actualKg, null, actualKg, WeightBasis.ACTUAL);
    }
}
