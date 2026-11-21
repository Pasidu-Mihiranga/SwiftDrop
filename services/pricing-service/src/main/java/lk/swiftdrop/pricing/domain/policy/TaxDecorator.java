package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.math.BigDecimal;

/**
 * Adds tax on the finished fee. The outermost rule, so it is charged on every line beneath it,
 * discounts included, and the customer's total is the tax-inclusive amount they pay.
 */
public final class TaxDecorator extends PricingDecorator {

    public TaxDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.TAX;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        PricingSettings settings = context.rates().settings();
        if (settings == null) {
            return BigDecimal.ZERO;
        }
        return chargedSoFar.multiply(settings.taxPct());
    }
}
