package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

/**
 * A priced quote, before it is given an identity and an expiry.
 *
 * @param weight     both measures and the one that was billed
 * @param sizeClass  the class actually charged, which may not be the one the customer selected
 */
public record PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone,
                            FeeBreakdown breakdown, ChargeableWeight weight, SizeClassRate sizeClass) {

    /** A quote for an unmeasured parcel: billed on what it weighs, in the class declared. */
    public PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone,
                         FeeBreakdown breakdown, SizeClassRate sizeClass) {
        this(input, fromZone, toZone, breakdown, ChargeableWeight.actual(input.weightKg()), sizeClass);
    }
}
