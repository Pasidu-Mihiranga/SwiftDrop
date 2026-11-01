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
        return soFar.plus(PricingRule.BASE, baseFee(context));
    }

    /**
     * A merchant that negotiated a flat price for this lane pays that instead of the card's.
     * It replaces the charge rather than reducing it, so it cannot be expressed as a discount.
     */
    private static java.math.BigDecimal baseFee(PricingContext context) {
        var card = context.rates().card();
        var agreement = context.rates().merchantAgreement();
        if (agreement == null) {
            return card.baseFee();
        }
        return agreement.overrideFor(card.fromZone(), card.toZone())
                .map(lk.swiftdrop.pricing.domain.model.LaneOverride::flatBaseFee)
                .filter(fee -> fee != null)
                .orElseGet(card::baseFee);
    }
}
