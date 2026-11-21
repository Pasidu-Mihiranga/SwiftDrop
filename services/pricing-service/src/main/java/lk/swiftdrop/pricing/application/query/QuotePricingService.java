package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.DistanceBandRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictLocator;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingRepository;
import lk.swiftdrop.pricing.application.port.out.InsuranceBandRepository;
import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.ChargeableWeight;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.VehicleType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves configured coverage and size limits before calculating a fee. */
@Service
@Transactional(readOnly = true)
public class QuotePricingService {

    /** Most handling options one quote may carry; the API validates the same bound. */
    public static final int MAX_SPECIAL_HANDLING = 5;

    private final DistrictAssignmentRepository districts;
    private final SizeClassRepository sizeClasses;
    private final RateCardSelectionService rateCards;
    private final VolumetricDivisorRepository divisors;
    private final WeightTierRepository weightTiers;
    private final RemoteAreaSurchargeRepository remoteAreas;
    private final InsuranceBandRepository insuranceBands;
    private final MerchantAgreementRepository merchantAgreements;
    private final FeeCalculator calculator;
    private final PricingSettingsRepository settings;
    private final DistanceBandRepository distanceBands;
    private final SpecialHandlingRepository specialHandling;
    private final SameDayVehicleRepository vehicles;
    private final DistrictLocator locator;

    @Autowired
    public QuotePricingService(DistrictAssignmentRepository districts, SizeClassRepository sizeClasses,
                               RateCardSelectionService rateCards, VolumetricDivisorRepository divisors,
                               WeightTierRepository weightTiers, RemoteAreaSurchargeRepository remoteAreas,
                               InsuranceBandRepository insuranceBands,
                               MerchantAgreementRepository merchantAgreements, FeeCalculator calculator,
                               PricingSettingsRepository settings, DistanceBandRepository distanceBands,
                               SpecialHandlingRepository specialHandling, SameDayVehicleRepository vehicles,
                               DistrictLocator locator) {
        this.districts = districts;
        this.sizeClasses = sizeClasses;
        this.rateCards = rateCards;
        this.divisors = divisors;
        this.weightTiers = weightTiers;
        this.remoteAreas = remoteAreas;
        this.insuranceBands = insuranceBands;
        this.merchantAgreements = merchantAgreements;
        this.calculator = calculator;
        this.settings = settings;
        this.distanceBands = distanceBands;
        this.specialHandling = specialHandling;
        this.vehicles = vehicles;
        this.locator = locator;
    }

    /**
     * Prices with none of the configuration V16 added: no line haul, fuel, tax, minimum, handling
     * or vehicle limits, and no check that a map point lies in its district. Kept so a caller
     * that only cares about the original rules does not have to stub the new ones.
     */
    public QuotePricingService(DistrictAssignmentRepository districts, SizeClassRepository sizeClasses,
                               RateCardSelectionService rateCards, VolumetricDivisorRepository divisors,
                               WeightTierRepository weightTiers, RemoteAreaSurchargeRepository remoteAreas,
                               InsuranceBandRepository insuranceBands,
                               MerchantAgreementRepository merchantAgreements, FeeCalculator calculator) {
        this(districts, sizeClasses, rateCards, divisors, weightTiers, remoteAreas, insuranceBands,
                merchantAgreements, calculator, on -> Optional.empty(), on -> List.of(), List::of,
                NO_VEHICLES, null);
    }

    private static final SameDayVehicleRepository NO_VEHICLES = new SameDayVehicleRepository() {
        @Override
        public Optional<SameDayVehicle> findActive(VehicleType type) {
            return Optional.empty();
        }

        @Override
        public List<SameDayVehicle> findAllActive() {
            return List.of();
        }
    };

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
        // A map point is checked before anything is priced: a pin dropped in Kandy on a quote
        // for Colombo would otherwise be measured from Kandy and charged as Colombo.
        requirePointInDistrict(input.origin(), input.fromDistrict());
        requirePointInDistrict(input.destination(), input.toDistrict());
        ZoneCode fromZone = activeZone(input.fromDistrict());
        ZoneCode toZone = activeZone(input.toDistrict());
        boolean sameDay = input.serviceLevel() == ServiceLevel.SAME_DAY;
        // A same-day job with nothing to measure is refused rather than quietly priced off the
        // zone matrix, which would be wrong and would look exactly like a correct answer.
        if (sameDay && (distance == null || distanceRate == null)) {
            throw new BusinessRuleViolationException("DISTANCE_UNAVAILABLE",
                    "Same-day delivery cannot be priced without a measurable route");
        }

        ChargeableWeight weight = ChargeableWeightPolicy.of(input.weightKg(), input.dimensions(),
                divisors.findInForce(quoteDate).orElse(null));
        SizeClassRate charged = chargedSizeClass(input);
        DistanceBand band = sameDay ? null : distanceBand(quoteDate, distance);
        SameDayVehicle vehicle = sameDay ? vehicle(input.effectiveVehicleType(), weight, distance) : null;
        List<SpecialHandlingOption> handling = handlingOptions(input.specialHandling());

        RateCard card = rateCards.select(fromZone, toZone, quoteDate);
        MerchantAgreement agreement =
                merchantAgreements.findInForce(input.merchantId(), quoteDate).orElse(null);
        PricingContext.Rates rates = new PricingContext.Rates(card, weightTiers.findByRateCard(card.id()),
                remoteAreas.findInForce(input.fromDistrict(), quoteDate).orElse(null),
                remoteAreas.findInForce(input.toDistrict(), quoteDate).orElse(null),
                insuranceBands.findInForce(quoteDate), agreement, distanceRate, distance,
                band, settings.findInForce(quoteDate).orElse(null), handling, vehicle);
        PricingContext context = new PricingContext(
                new PricingContext.Parcel(weight.actualKg(), weight.chargeableKg(), charged,
                        input.serviceLevel(), input.codAmount(), input.declaredValue()),
                rates);
        return new PreparedQuote(input, fromZone, toZone, calculator.calculate(context), weight, charged,
                agreement == null ? null : agreement.id(), distance,
                band == null ? null : band.transitDays(input.serviceLevel()),
                input.effectiveVehicleType(),
                handling.stream().map(SpecialHandlingOption::code).toList(),
                PricingVersion.of(rates, charged));
    }

    private void requirePointInDistrict(GeoPoint point, String district) {
        if (point == null || locator == null) {
            return;
        }
        String located = locator.districtAt(point).orElseThrow(() -> new BusinessRuleViolationException(
                "OUTSIDE_COVERAGE", "That location is outside every district SwiftDrop serves"));
        if (!located.equalsIgnoreCase(district.strip())) {
            throw new BusinessRuleViolationException("LOCATION_DISTRICT_MISMATCH",
                    "The location is in " + located + ", not in " + district.strip());
        }
    }

    /**
     * The band the lane falls in, or null when no bands are configured. Two bands claiming the
     * same distance is a configuration error, as two rate cards for one lane are: it is refused
     * rather than resolved by picking one.
     */
    private DistanceBand distanceBand(LocalDate quoteDate, LaneDistance distance) {
        List<DistanceBand> inForce = distanceBands.findBandsInForce(quoteDate);
        if (inForce.isEmpty()) {
            return null;
        }
        if (distance == null) {
            throw new BusinessRuleViolationException("DISTANCE_UNAVAILABLE",
                    "Line haul cannot be priced without a measurable route");
        }
        List<DistanceBand> matching = inForce.stream().filter(band -> band.contains(distance.kilometres())).toList();
        if (matching.isEmpty()) {
            throw new BusinessRuleViolationException("NO_DISTANCE_BAND",
                    "No distance band covers " + distance.kilometres() + " km");
        }
        if (matching.size() > 1) {
            throw new IllegalStateException("Distance bands overlap at " + distance.kilometres() + " km");
        }
        return matching.getFirst();
    }

    /** The vehicle a same-day job is priced for, or null when vehicle limits are not configured. */
    private SameDayVehicle vehicle(VehicleType type, ChargeableWeight weight, LaneDistance distance) {
        if (vehicles.findAllActive().isEmpty()) {
            return null;
        }
        SameDayVehicle vehicle = vehicles.findActive(type).orElseThrow(() -> new BusinessRuleViolationException(
                "VEHICLE_NOT_OFFERED", "Same-day delivery is not offered by " + type));
        if (!vehicle.carries(weight.chargeableKg())) {
            throw new BusinessRuleViolationException("SAME_DAY_TOO_HEAVY",
                    "The parcel is too heavy or bulky for a same-day " + type);
        }
        if (!vehicle.reaches(distance.kilometres())) {
            throw new BusinessRuleViolationException("SAME_DAY_OUT_OF_RANGE",
                    "That trip is too far for a same-day " + type);
        }
        return vehicle;
    }

    /**
     * Resolves the codes asked for against the configured list. A repeated code is charged
     * once; an unknown or retired one is refused, because silently dropping it would quote a
     * price for a service the customer will not get.
     */
    private List<SpecialHandlingOption> handlingOptions(List<String> requested) {
        if (requested.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = new LinkedHashSet<>();
        requested.forEach(code -> wanted.add(code.strip().toUpperCase(Locale.ROOT)));
        if (wanted.size() > MAX_SPECIAL_HANDLING) {
            throw new BusinessRuleViolationException("TOO_MANY_SPECIAL_HANDLING",
                    "At most " + MAX_SPECIAL_HANDLING + " handling options can be chosen");
        }
        List<SpecialHandlingOption> available = specialHandling.findActive();
        Set<String> known = new LinkedHashSet<>();
        available.forEach(option -> known.add(option.code()));
        for (String code : wanted) {
            if (!known.contains(code)) {
                throw new BusinessRuleViolationException("UNKNOWN_SPECIAL_HANDLING",
                        "Special handling option " + code + " is not available");
            }
        }
        // In configured order, so the same selection always reads the same way.
        return available.stream().filter(option -> wanted.contains(option.code())).toList();
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
