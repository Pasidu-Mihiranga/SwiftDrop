package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.InsuranceBand;
import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;

/**
 * Charges the premium for covering what the customer says the parcel is worth.
 *
 * <p>The premium comes from the declared value, not from the delivery fee, so it does not
 * compound with the rules beneath it. A customer who declares nothing buys no cover and is
 * charged nothing, which is why the line simply disappears rather than being reported as zero.
 */
public final class InsuranceSurchargeDecorator extends PricingDecorator {

    public InsuranceSurchargeDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.INSURANCE;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        BigDecimal declaredValue = context.parcel().declaredValue();
        if (declaredValue == null || declaredValue.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return context.rates().insuranceBands().stream()
                .filter(band -> band.covers(declaredValue))
                .findFirst()
                .map(band -> band.premiumFor(declaredValue))
                .orElse(BigDecimal.ZERO);
    }

    /** Exposed so a caller can show the band that applied without repeating the search. */
    public static InsuranceBand bandFor(java.util.List<InsuranceBand> bands, BigDecimal declaredValue) {
        if (declaredValue == null || declaredValue.signum() <= 0) {
            return null;
        }
        return bands.stream().filter(band -> band.covers(declaredValue)).findFirst().orElse(null);
    }
}
