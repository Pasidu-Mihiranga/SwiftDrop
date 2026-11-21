package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.util.List;
import java.util.UUID;

/**
 * A priced quote, before it is given an identity and an expiry.
 *
 * @param weight               both measures and the one that was billed
 * @param sizeClass            the class actually charged, which may not be the one the customer selected
 * @param agreementId          the negotiated terms applied, or null when the counter price was used
 * @param distance             how far the job travels; null when nothing was measured
 * @param estimatedTransitDays from the distance band; null for same-day or when bands are not configured
 * @param vehicleType          same-day only: the vehicle the fee was priced for
 * @param specialHandling      the handling option codes charged for, in configured order
 * @param pricingVersion       a short fingerprint of the configuration that produced the fee
 */
public record PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone,
                            FeeBreakdown breakdown, ChargeableWeight weight, SizeClassRate sizeClass,
                            UUID agreementId, LaneDistance distance, Integer estimatedTransitDays,
                            VehicleType vehicleType, List<String> specialHandling, String pricingVersion) {

    public PreparedQuote {
        specialHandling = specialHandling == null ? List.of() : List.copyOf(specialHandling);
    }

    /** A quote priced without any of the configuration V16 added. */
    public PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone, FeeBreakdown breakdown,
                         ChargeableWeight weight, SizeClassRate sizeClass, UUID agreementId, LaneDistance distance) {
        this(input, fromZone, toZone, breakdown, weight, sizeClass, agreementId, distance, null, null, List.of(),
                null);
    }

    /** A quote for an unmeasured parcel: billed on what it weighs, in the class declared. */
    public PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone,
                         FeeBreakdown breakdown, SizeClassRate sizeClass) {
        this(input, fromZone, toZone, breakdown, ChargeableWeight.actual(input.weightKg()), sizeClass,
                null, null);
    }
}
