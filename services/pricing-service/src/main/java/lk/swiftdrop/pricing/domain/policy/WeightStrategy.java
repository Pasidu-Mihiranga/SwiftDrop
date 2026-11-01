package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.MonetaryRounding;
import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The charge for weight. Part kilograms are billed as whole ones, so 2.1 kg is charged as 3 kg,
 * which matches how couriers are paid for a parcel they have to carry regardless.
 */
public final class WeightStrategy implements PricingStrategy {

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        BigDecimal chargeableKg = context.parcel().chargeableWeightKg().setScale(0, RoundingMode.CEILING);
        return soFar.plus(PricingRule.WEIGHT, context.rates().card().perKgFee().multiply(chargeableKg));
    }

    /** Exposed so the quote breakdown and tests can agree on what "chargeable weight" means. */
    public static BigDecimal chargeableKilograms(BigDecimal weightKg) {
        return MonetaryRounding.round(weightKg.setScale(0, RoundingMode.CEILING));
    }
}
