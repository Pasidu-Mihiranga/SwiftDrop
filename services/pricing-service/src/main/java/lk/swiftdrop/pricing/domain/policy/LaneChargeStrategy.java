package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;

/**
 * Charges for carrying the parcel, by whichever measure the service level is priced on.
 *
 * <p>This is the one place where the service level picks a strategy rather than scaling a
 * surcharge. Same-day work is priced on the road distance actually travelled; everything else
 * is priced by the zone matrix. Keeping the choice in a named class means the calculator still
 * builds exactly one pipeline, shared across every request, with one decision point inside it
 * rather than two chains to keep in step.
 */
public final class LaneChargeStrategy implements PricingStrategy {

    private final PricingStrategy zoneMatrix;
    private final PricingStrategy distance;

    public LaneChargeStrategy() {
        this(new ZoneMatrixStrategy(), new DistanceStrategy());
    }

    public LaneChargeStrategy(PricingStrategy zoneMatrix, PricingStrategy distance) {
        this.zoneMatrix = zoneMatrix;
        this.distance = distance;
    }

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        return context.parcel().serviceLevel() == ServiceLevel.SAME_DAY
                ? distance.price(context, soFar)
                : zoneMatrix.price(context, soFar);
    }
}
