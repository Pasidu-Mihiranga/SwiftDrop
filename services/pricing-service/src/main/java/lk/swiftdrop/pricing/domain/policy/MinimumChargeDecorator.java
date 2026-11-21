package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.math.BigDecimal;

/**
 * Tops the delivery charge up to the configured minimum.
 *
 * <p>It sits directly above the charges for carrying the parcel (lane, line haul, weight, size,
 * express) and below everything optional. A short, light parcel still costs a courier a trip,
 * so the floor applies to the trip; fuel is then charged on the floored amount, and the extras a
 * customer chooses to buy are added on top rather than being swallowed by the floor.
 *
 * <p>It is a separate line, not a silent adjustment, so the customer can see why a short hop
 * costs what it does and the lines still sum to the fee.
 */
public final class MinimumChargeDecorator extends PricingDecorator {

    public MinimumChargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.MINIMUM_CHARGE;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        PricingSettings settings = context.rates().settings();
        if (settings == null) {
            return BigDecimal.ZERO;
        }
        return settings.minimumCharge().subtract(chargedSoFar).max(BigDecimal.ZERO);
    }
}
