package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;

import java.math.BigDecimal;

/**
 * The charge for a same-day job, measured along the road rather than banded by province.
 *
 * <p>Flag fall plus a rate per kilometre. The flag fall is there because going to collect at
 * all costs money before any distance is covered, and the minimum chargeable distance is there
 * because a job across the street is short, not free.
 */
public final class DistanceStrategy implements PricingStrategy {

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        DistanceRate rate = context.rates().distanceRate();
        LaneDistance distance = context.rates().laneDistance();
        if (rate == null || distance == null) {
            // Nothing to measure with. The caller refuses the quote rather than quietly
            // falling back to a zone price that would be wrong and invisible.
            return soFar;
        }
        BigDecimal chargeable = rate.chargeableKilometres(distance.kilometres());
        BigDecimal charge = rate.flagFall().add(rate.perKmFee().multiply(chargeable));
        // The vehicle scales the whole trip, flag fall included: sending a van to collect costs
        // more than sending a bike before a single kilometre is driven.
        SameDayVehicle vehicle = context.rates().vehicle();
        return soFar.plus(PricingRule.DISTANCE,
                vehicle == null ? charge : charge.multiply(vehicle.rateMultiplier()));
    }
}
