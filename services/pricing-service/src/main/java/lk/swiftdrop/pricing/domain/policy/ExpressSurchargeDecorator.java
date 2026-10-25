package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;

import java.math.BigDecimal;

/** Charges express delivery on the size-inclusive total. Standard service adds nothing. */
public final class ExpressSurchargeDecorator extends PricingDecorator {

    public ExpressSurchargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.EXPRESS;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        if (context.serviceLevel() != ServiceLevel.EXPRESS) {
            return BigDecimal.ZERO;
        }
        return chargedSoFar.multiply(context.card().expressMultiplier().subtract(BigDecimal.ONE));
    }
}
