package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;

/**
 * The distance-band charge for a zone-priced parcel.
 *
 * <p>The zone rate card prices the lane between two provinces, but two districts in the same
 * pair of provinces can be twenty kilometres apart or two hundred. The band corrects for that
 * with a flat charge per range of road distance.
 *
 * <p>Same-day work is skipped: it is already priced per kilometre by {@link DistanceStrategy},
 * and charging the distance twice would be wrong in a way no breakdown would reveal.
 */
public final class LineHaulStrategy implements PricingStrategy {

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        DistanceBand band = context.rates().distanceBand();
        if (band == null || context.parcel().serviceLevel() == ServiceLevel.SAME_DAY) {
            return soFar;
        }
        return soFar.plus(PricingRule.LINE_HAUL, band.lineHaulFee());
    }
}
