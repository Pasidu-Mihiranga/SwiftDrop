package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;

/**
 * Charges the size class as a proportion of what has been charged so far. A multiplier of 1
 * means the class carries no surcharge, so nothing is added.
 */
public final class SizeSurchargeDecorator extends PricingDecorator {

    public SizeSurchargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.SIZE;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        return chargedSoFar.multiply(context.sizeClass().multiplier().subtract(BigDecimal.ONE));
    }
}
