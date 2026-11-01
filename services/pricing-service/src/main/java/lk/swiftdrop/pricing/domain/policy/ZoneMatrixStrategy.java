package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;

/**
 * The flat charge for the lane, taken from the rate card selected for the origin and destination
 * zone pair. This is the only rule that reads the zone matrix, so changing how distance is
 * priced means changing rate-card data rather than code.
 */
public final class ZoneMatrixStrategy implements PricingStrategy {

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        return soFar.plus(PricingRule.BASE, context.rates().card().baseFee());
    }
}
