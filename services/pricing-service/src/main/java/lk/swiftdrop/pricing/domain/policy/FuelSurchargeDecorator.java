package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.math.BigDecimal;

/**
 * Adds the fuel surcharge as a fraction of the transport charge.
 *
 * <p>Charged on the floored transport total and nothing above it: fuel is burnt carrying the
 * parcel, not handling it, insuring it or collecting cash for it. Keeping it a separate,
 * configurable line means a change in fuel prices is one new settings row, not a repricing of
 * every rate card.
 */
public final class FuelSurchargeDecorator extends PricingDecorator {

    public FuelSurchargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.FUEL;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        PricingSettings settings = context.rates().settings();
        if (settings == null) {
            return BigDecimal.ZERO;
        }
        return chargedSoFar.multiply(settings.fuelSurchargePct());
    }
}
