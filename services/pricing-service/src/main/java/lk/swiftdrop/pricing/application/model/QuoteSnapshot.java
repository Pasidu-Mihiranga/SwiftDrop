package lk.swiftdrop.pricing.application.model;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.WeightBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable quote representation persisted and returned without recalculation.
 *
 * <p>Every component added after the first release is nullable, because a snapshot issued by
 * the previous release is still looked up after a deploy and simply has nothing to say about
 * them. See {@code RedisQuoteStore} for how an older value is read.
 *
 * @param weightKg           what the parcel weighs, as the customer gave it
 * @param chargeableWeightKg what it was billed as; differs when the parcel is bulky
 * @param volumetricWeightKg what its volume came to; null when no measurements were given
 * @param sizeClass          the class charged for
 * @param declaredSizeClass  the class the customer selected; differs when measurements moved it
 * @param declaredValue      what the parcel was insured for; null when sent without cover
 * @param merchantId         the merchant the quote was issued to; null for an anonymous customer
 * @param agreementId        the negotiated terms applied; null when none were
 * @param distanceKm         how far a same-day job travels; null for a zone-priced quote
 * @param distanceSource     whether that distance was routed or estimated
 */
public record QuoteSnapshot(UUID quoteId, Money fee, List<FeeBreakdown.Line> breakdown,
                            String fromZone, String toZone, String fromDistrict, String toDistrict,
                            BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                            BigDecimal codAmount, Instant issuedAt, Instant expiresAt,
                            BigDecimal chargeableWeightKg, BigDecimal volumetricWeightKg,
                            WeightBasis weightBasis, Dimensions dimensionsCm,
                            SizeClassCode declaredSizeClass, BigDecimal declaredValue,
                            UUID merchantId, UUID agreementId,
                            BigDecimal distanceKm, DistanceSource distanceSource) {

    public QuoteSnapshot {
        breakdown = List.copyOf(breakdown);
        // An older stored value carries none of these. Normalising here means no reader has to
        // remember that a quote from before this release bills on actual weight.
        chargeableWeightKg = chargeableWeightKg == null ? weightKg : chargeableWeightKg;
        weightBasis = weightBasis == null ? WeightBasis.ACTUAL : weightBasis;
        declaredSizeClass = declaredSizeClass == null ? sizeClass : declaredSizeClass;
    }

    /** A quote for a parcel the customer did not measure. */
    public QuoteSnapshot(UUID quoteId, Money fee, List<FeeBreakdown.Line> breakdown,
                         String fromZone, String toZone, String fromDistrict, String toDistrict,
                         BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                         BigDecimal codAmount, Instant issuedAt, Instant expiresAt) {
        this(quoteId, fee, breakdown, fromZone, toZone, fromDistrict, toDistrict, weightKg,
                sizeClass, serviceLevel, codAmount, issuedAt, expiresAt,
                weightKg, null, WeightBasis.ACTUAL, null, sizeClass, null, null, null, null, null);
    }

    /** Whether the parcel was measured into a class other than the one selected. */
    public boolean sizeClassWasDerived() {
        return declaredSizeClass != sizeClass;
    }

    public record Money(BigDecimal amount, String currency) { }
}
