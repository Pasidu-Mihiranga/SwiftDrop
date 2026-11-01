package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;

import java.math.BigDecimal;

/**
 * Charges for a collection or a delivery that is hard to reach.
 *
 * <p>Origin and destination are charged independently and summed into one line. A parcel going
 * from one remote district to another costs the courier two awkward journeys, not one, so both
 * are charged; reporting them as a single line keeps one rule to one line.
 *
 * <p>A flat amount, not a proportion: the difficulty is the trip, and it does not get worse
 * because the parcel is heavy or urgent.
 */
public final class RemoteAreaSurchargeDecorator extends PricingDecorator {

    public RemoteAreaSurchargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.REMOTE_AREA;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        return fee(context.rates().originRemoteArea()).add(fee(context.rates().destinationRemoteArea()));
    }

    private static BigDecimal fee(RemoteAreaSurcharge surcharge) {
        return surcharge == null ? BigDecimal.ZERO : surcharge.flatFee();
    }
}
