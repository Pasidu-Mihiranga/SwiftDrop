package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;

/**
 * Charges a commission for collecting cash on delivery. It is a fraction of the cash being
 * collected rather than of the delivery fee, so it does not compound with the other rules.
 */
public final class CodFeeDecorator extends PricingDecorator {

    public CodFeeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.COD;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        return context.parcel().codAmount().multiply(context.rates().card().codFeePct());
    }
}
