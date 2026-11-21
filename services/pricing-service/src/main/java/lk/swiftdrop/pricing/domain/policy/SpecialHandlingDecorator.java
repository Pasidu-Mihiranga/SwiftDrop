package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;

import java.math.BigDecimal;

/**
 * Charges for the handling options the customer asked for, as one line.
 *
 * <p>Flat fees, summed. Handling a fragile parcel carefully costs the same whatever the lane, so
 * none of these scale with the delivery charge. The options arrive already resolved against the
 * configured list; an unknown code is refused before pricing starts.
 */
public final class SpecialHandlingDecorator extends PricingDecorator {

    public SpecialHandlingDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.SPECIAL_HANDLING;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        return context.rates().specialHandling().stream()
                .map(SpecialHandlingOption::fee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
