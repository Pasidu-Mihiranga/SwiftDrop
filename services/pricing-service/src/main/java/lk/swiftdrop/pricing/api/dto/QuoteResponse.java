package lk.swiftdrop.pricing.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import lk.swiftdrop.pricing.domain.model.WeightBasis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The quote as published in the OpenAPI contract.
 *
 * <p>Issuing a quote and looking one up return this same shape, so a caller validating a quote
 * sees exactly what the customer was shown. It is a separate type from the stored snapshot on
 * purpose: what is kept in Redis and what is published on the wire evolve for different reasons,
 * and the wire shape is the one other teams depend on.
 *
 * <p>Fields that do not apply are left out rather than sent as null, so a quote priced on
 * weight alone looks exactly as it did before volumetric weight existed.
 *
 * @param chargeableWeightKg what the parcel was billed as; equals weightKg unless it is bulky
 * @param volumetricWeightKg what its volume came to; absent when no measurements were given
 * @param declaredSizeClass  present only when measurements put the parcel in another class
 * @param agreementId        the negotiated terms applied; absent for a counter-price quote
 * @param distanceSource     ESTIMATED means no router answered and the figure is approximate
 * @param estimatedTransitDays from the distance band; absent for same-day
 * @param vehicleType        same-day only: the vehicle the fee was priced for
 * @param specialHandling    the handling options charged for; absent when none
 * @param pricingVersion     a short fingerprint of the configuration that produced the fee
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuoteResponse(UUID quoteId, Money fee, List<BreakdownLineResponse> breakdown,
                            String fromZone, String toZone, String fromDistrict, String toDistrict,
                            BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                            BigDecimal codAmount, Instant issuedAt, Instant expiresAt,
                            BigDecimal chargeableWeightKg, BigDecimal volumetricWeightKg,
                            WeightBasis weightBasis, SizeClassCode declaredSizeClass,
                            BigDecimal declaredValue, UUID agreementId,
                            BigDecimal distanceKm, DistanceSource distanceSource,
                            Integer estimatedTransitDays, VehicleType vehicleType,
                            List<String> specialHandling, String pricingVersion,
                            String fromAddress, String toAddress) {

    public QuoteResponse(UUID quoteId, Money fee, List<BreakdownLineResponse> breakdown,
                         String fromZone, String toZone, String fromDistrict, String toDistrict,
                         BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                         BigDecimal codAmount, Instant issuedAt, Instant expiresAt,
                         BigDecimal chargeableWeightKg, BigDecimal volumetricWeightKg,
                         WeightBasis weightBasis, SizeClassCode declaredSizeClass,
                         BigDecimal declaredValue, UUID agreementId,
                         BigDecimal distanceKm, DistanceSource distanceSource,
                         Integer estimatedTransitDays, VehicleType vehicleType,
                         List<String> specialHandling, String pricingVersion) {
        this(quoteId, fee, breakdown, fromZone, toZone, fromDistrict, toDistrict, weightKg, sizeClass,
                serviceLevel, codAmount, issuedAt, expiresAt, chargeableWeightKg, volumetricWeightKg,
                weightBasis, declaredSizeClass, declaredValue, agreementId, distanceKm, distanceSource,
                estimatedTransitDays, vehicleType, specialHandling, pricingVersion, null, null);
    }

    /** An amount and its currency. Mirrors the shared Money schema in common.v1.yaml. */
    public record Money(BigDecimal amount, String currency) { }

    /** One rule's contribution to the fee. The lines always sum exactly to the total. */
    public record BreakdownLineResponse(PricingRule rule, BigDecimal amount) { }
}
