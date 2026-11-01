package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;

/**
 * Takes a merchant's negotiated discount off the delivery charge.
 *
 * <p>The only rule that charges a negative amount. It sits above the charges for carrying the
 * parcel and below the premium and the cash commission, so the discount applies to the delivery
 * and not to those two: a premium is cover being bought and a commission is cash being handled,
 * and neither is SwiftDrop's price to discount.
 *
 * <p>The discount can never take the running total below zero. A misconfigured agreement should
 * make a delivery free, not make SwiftDrop pay for it.
 */
public final class MerchantDiscountDecorator extends PricingDecorator {

    public MerchantDiscountDecorator(PricingStrategy delegate) {
        super(delegate);
    }

    @Override
    protected PricingRule rule() {
        return PricingRule.MERCHANT_DISCOUNT;
    }

    @Override
    protected BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar) {
        MerchantAgreement agreement = context.rates().merchantAgreement();
        if (agreement == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = agreement.discountPctFor(context.rates().card().fromZone(),
                context.rates().card().toZone());
        BigDecimal discount = chargedSoFar.multiply(pct).add(agreement.discountFlat());
        return discount.min(chargedSoFar).negate();
    }
}
