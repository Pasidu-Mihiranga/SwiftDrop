package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.InsuranceBandRepository;
import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import lk.swiftdrop.pricing.domain.policy.ChargeableWeightPolicy;
import lk.swiftdrop.pricing.domain.policy.FeeCalculator;
import lk.swiftdrop.pricing.domain.policy.PricingContext;
import lk.swiftdrop.pricing.domain.policy.SizeClassSelector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Resolves configured coverage and size limits before calculating a fee. */
@Service
@Transactional(readOnly = true)
public class QuotePricingService {

    private final DistrictAssignmentRepository districts;
    private final SizeClassRepository sizeClasses;
    private final RateCardSelectionService rateCards;
    private final VolumetricDivisorRepository divisors;
    private final WeightTierRepository weightTiers;
    private final RemoteAreaSurchargeRepository remoteAreas;
    private final InsuranceBandRepository insuranceBands;
    private final MerchantAgreementRepository merchantAgreements;
    private final FeeCalculator calculator;

    public QuotePricingService(DistrictAssignmentRepository districts, SizeClassRepository sizeClasses,
                               RateCardSelectionService rateCards, VolumetricDivisorRepository divisors,
                               WeightTierRepository weightTiers, RemoteAreaSurchargeRepository remoteAreas,
                               InsuranceBandRepository insuranceBands,
                               MerchantAgreementRepository merchantAgreements, FeeCalculator calculator) {
        this.districts = districts;
        this.sizeClasses = sizeClasses;
        this.rateCards = rateCards;
        this.divisors = divisors;
        this.weightTiers = weightTiers;
        this.remoteAreas = remoteAreas;
        this.insuranceBands = insuranceBands;
        this.merchantAgreements = merchantAgreements;
        this.calculator = calculator;
    }

    public PreparedQuote prepare(QuotePricingInput input, LocalDate quoteDate) {
        return prepare(input, quoteDate, null, null);
    }

    /**
     * @param distance measured before this call, outside any transaction, because measuring a
     *                 lane may mean a network round trip and a read transaction must not be
     *                 held open across one
     */
    public PreparedQuote prepare(QuotePricingInput input, LocalDate quoteDate, LaneDistance distance,
                                 DistanceRate distanceRate) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(quoteDate, "quoteDate");
        ZoneCode fromZone = activeZone(input.fromDistrict());
        ZoneCode toZone = activeZone(input.toDistrict());
        // A same-day job with nothing to measure is refused rather than quietly priced off the
        // zone matrix, which would be wrong and would look exactly like a correct answer.
        if (input.serviceLevel() == ServiceLevel.SAME_DAY && (distance == null || distanceRate == null)) {
            throw new BusinessRuleViolationException("DISTANCE_UNAVAILABLE",
                    "Same-day delivery cannot be priced without a measurable route");
        }

        ChargeableWeight weight = ChargeableWeightPolicy.of(input.weightKg(), input.dimensions(),
                divisors.findInForce(quoteDate).orElse(null));
        SizeClassRate charged = chargedSizeClass(input);

        RateCard card = rateCards.select(fromZone, toZone, quoteDate);
        MerchantAgreement agreement =
                merchantAgreements.findInForce(input.merchantId(), quoteDate).orElse(null);
        PricingContext context = new PricingContext(
                new PricingContext.Parcel(weight.actualKg(), weight.chargeableKg(), charged,
                        input.serviceLevel(), input.codAmount(), input.declaredValue()),
                new PricingContext.Rates(card, weightTiers.findByRateCard(card.id()),
                        remoteAreas.findInForce(input.fromDistrict(), quoteDate).orElse(null),
                        remoteAreas.findInForce(input.toDistrict(), quoteDate).orElse(null),
                        insuranceBands.findInForce(quoteDate), agreement, distanceRate, distance));
        return new PreparedQuote(input, fromZone, toZone, calculator.calculate(context), weight, charged,
                agreement == null ? null : agreement.id(), distance);
    }

    /**
     * The class the parcel is charged at, and the only class its weight is checked against.
     *
     * <p>Measurements decide it where they were given: the cheapest class the parcel genuinely
     * fits by both weight and shape. Checking the weight against the declared class as well
     * would mean a customer who measures their parcel honestly is refused a quote that the
     * same parcel would have got had they said nothing, which is precisely backwards.
     *
     * <p>With no measurements there is nothing to derive from, so the declared class stands and
     * its weight limit is the one that applies.
     */
    private SizeClassRate chargedSizeClass(QuotePricingInput input) {
        if (input.dimensions() == null) {
            SizeClassRate declared = sizeClasses.findByCode(input.sizeClass())
                    .orElseThrow(() -> new IllegalStateException(
                            "Size class " + input.sizeClass() + " is not configured"));
            if (!declared.accepts(input.weightKg())) {
                throw new BusinessRuleViolationException("SIZE_CLASS_WEIGHT_EXCEEDED",
                        "Weight exceeds the configured limit for size class " + input.sizeClass());
            }
            return declared;
        }
        return SizeClassSelector.select(sizeClasses.findAll(), input.weightKg(), input.dimensions())
                .orElseThrow(() -> new BusinessRuleViolationException("PARCEL_TOO_LARGE",
                        "No size class accepts a parcel of these dimensions and weight"));
    }

    private ZoneCode activeZone(String district) {
        return districts.findActiveZoneCodeByDistrict(district)
                .orElseThrow(() -> new BusinessRuleViolationException("DISTRICT_NOT_SERVICED",
                        "District is not served by an active zone"));
    }
}
