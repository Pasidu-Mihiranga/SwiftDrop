package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.MonetaryRounding;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.WeightTier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The charge for weight.
 *
 * <p>Part kilograms are billed as whole ones, so 2.1 kg is charged as 3 kg: the courier carries
 * the parcel regardless. The weight billed is the chargeable weight, which for a bulky parcel is
 * what it displaces rather than what it weighs.
 *
 * <p>Where the lane has bands, they are marginal. A 12 kg parcel on bands of 5 kg then open is
 * charged five kilograms at the first rate and seven at the second, not twelve at whichever band
 * it lands in. A flat-at-band rule would make the twelfth kilogram cost more than the eleventh
 * by a jump the customer cannot see, and would price 5.1 kg above 5 kg by a visible cliff.
 *
 * <p>A lane with no bands charges its flat per-kilogram rate for every kilogram, which is what
 * every lane does until an administrator configures otherwise.
 */
public final class TieredWeightStrategy implements PricingStrategy {

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        BigDecimal chargeableKg = chargeableKilograms(context.parcel().chargeableWeightKg());
        List<WeightTier> tiers = context.rates().weightTiers();
        BigDecimal amount = tiers.isEmpty()
                ? context.rates().card().perKgFee().multiply(chargeableKg)
                : acrossBands(tiers, chargeableKg);
        return soFar.plus(PricingRule.WEIGHT, amount);
    }

    private static BigDecimal acrossBands(List<WeightTier> tiers, BigDecimal chargeableKg) {
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        for (WeightTier tier : tiers) {
            if (settled.compareTo(chargeableKg) >= 0) {
                break;
            }
            BigDecimal bandTop = tier.isOpenEnded() ? chargeableKg : tier.upToKg().min(chargeableKg);
            BigDecimal inBand = bandTop.subtract(settled);
            if (inBand.signum() <= 0) {
                continue;
            }
            charged = charged.add(tier.perKgFee().multiply(inBand));
            settled = bandTop;
        }
        // A configuration whose bands stop short of the parcel leaves weight uncharged. The
        // last band's rate carries the remainder rather than the parcel travelling free.
        if (settled.compareTo(chargeableKg) < 0 && !tiers.isEmpty()) {
            BigDecimal remainder = chargeableKg.subtract(settled);
            charged = charged.add(tiers.get(tiers.size() - 1).perKgFee().multiply(remainder));
        }
        return charged;
    }

    /** Exposed so the quote breakdown and tests can agree on what "chargeable weight" means. */
    public static BigDecimal chargeableKilograms(BigDecimal weightKg) {
        return MonetaryRounding.round(weightKg.setScale(0, RoundingMode.CEILING));
    }
}
