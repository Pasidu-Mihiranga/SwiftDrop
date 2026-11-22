package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.DistanceBandRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictLocator;
import lk.swiftdrop.pricing.application.port.out.InsuranceBandRepository;
import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsRepository;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingRepository;
import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import lk.swiftdrop.pricing.domain.policy.FeeCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules V16 added, as the pricing use case applies them before and around the fee chain. */
class QuotePricingExtensionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final ZoneCode WP = ZoneCode.of("WP");
    private static final ZoneCode CP = ZoneCode.of("CP");
    private static final GeoPoint FORT = GeoPoint.of("6.9344", "79.8428");
    private static final GeoPoint KANDY_TOWN = GeoPoint.of("7.2936", "80.6413");

    private static final List<DistanceBand> BANDS = List.of(
            new DistanceBand(UUID.randomUUID(), new BigDecimal("0"), new BigDecimal("30"),
                    new BigDecimal("0.00"), 1, 1),
            new DistanceBand(UUID.randomUUID(), new BigDecimal("30"), new BigDecimal("150"),
                    new BigDecimal("150.00"), 2, 1),
            new DistanceBand(UUID.randomUUID(), new BigDecimal("150"), null,
                    new BigDecimal("450.00"), 3, 2));
    private static final SameDayVehicle BIKE = new SameDayVehicle(VehicleType.BIKE, new BigDecimal("10"),
            new BigDecimal("40"), new BigDecimal("1.000"));
    private static final SameDayVehicle VAN = new SameDayVehicle(VehicleType.VAN, new BigDecimal("100"),
            new BigDecimal("120"), new BigDecimal("1.800"));
    private static final DistanceRate PER_KM = new DistanceRate(new BigDecimal("300.00"), new BigDecimal("55.00"),
            new BigDecimal("3"), new BigDecimal("1.3"));

    private DistrictAssignmentRepository districts;
    private SizeClassRepository sizeClasses;
    private RateCardSelectionService rateCards;
    private PricingSettingsRepository settings;
    private DistanceBandRepository bands;
    private SpecialHandlingRepository handling;
    private SameDayVehicleRepository vehicles;
    private DistrictLocator locator;
    private QuotePricingService service;

    @BeforeEach
    void setUp() {
        districts = mock(DistrictAssignmentRepository.class);
        sizeClasses = mock(SizeClassRepository.class);
        rateCards = mock(RateCardSelectionService.class);
        VolumetricDivisorRepository divisors = mock(VolumetricDivisorRepository.class);
        when(divisors.findInForce(any())).thenReturn(Optional.empty());
        WeightTierRepository weightTiers = mock(WeightTierRepository.class);
        when(weightTiers.findByRateCard(any())).thenReturn(List.of());
        RemoteAreaSurchargeRepository remoteAreas = mock(RemoteAreaSurchargeRepository.class);
        when(remoteAreas.findInForce(any(), any())).thenReturn(Optional.empty());
        InsuranceBandRepository insuranceBands = mock(InsuranceBandRepository.class);
        when(insuranceBands.findInForce(any())).thenReturn(List.of());
        MerchantAgreementRepository agreements = mock(MerchantAgreementRepository.class);
        when(agreements.findInForce(any(), any())).thenReturn(Optional.empty());
        settings = mock(PricingSettingsRepository.class);
        when(settings.findInForce(any())).thenReturn(Optional.of(new PricingSettings(UUID.randomUUID(),
                new BigDecimal("0.05"), new BigDecimal("0.18"), new BigDecimal("350.00"))));
        bands = mock(DistanceBandRepository.class);
        when(bands.findBandsInForce(any())).thenReturn(BANDS);
        handling = mock(SpecialHandlingRepository.class);
        when(handling.findActive()).thenReturn(List.of(
                new SpecialHandlingOption("FRAGILE", "Fragile", new BigDecimal("250.00")),
                new SpecialHandlingOption("SIGNATURE", "Signature", new BigDecimal("100.00"))));
        vehicles = mock(SameDayVehicleRepository.class);
        when(vehicles.findAllActive()).thenReturn(List.of(BIKE, VAN));
        when(vehicles.findActive(VehicleType.BIKE)).thenReturn(Optional.of(BIKE));
        when(vehicles.findActive(VehicleType.VAN)).thenReturn(Optional.of(VAN));
        locator = mock(DistrictLocator.class);
        when(locator.districtAt(FORT)).thenReturn(Optional.of("Colombo"));
        when(locator.districtAt(KANDY_TOWN)).thenReturn(Optional.of("Kandy"));

        service = new QuotePricingService(districts, sizeClasses, rateCards, divisors, weightTiers, remoteAreas,
                insuranceBands, agreements, new FeeCalculator(), settings, bands, handling, vehicles, locator);
        when(districts.findActiveZoneCodeByDistrict("Colombo")).thenReturn(Optional.of(WP));
        when(districts.findActiveZoneCodeByDistrict("Kandy")).thenReturn(Optional.of(CP));
        when(sizeClasses.findByCode(SizeClassCode.M)).thenReturn(Optional.of(new SizeClassRate(SizeClassCode.M,
                new BigDecimal("50"), Dimensions.of("40", "30", "20"), new BigDecimal("1.000"))));
        when(rateCards.select(WP, CP, TODAY)).thenReturn(new RateCard(UUID.randomUUID(), WP, CP,
                new BigDecimal("475.00"), new BigDecimal("78.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), TODAY, null));
    }

    private static QuotePricingInput standard(GeoPoint origin, GeoPoint destination, List<String> handling) {
        return new QuotePricingInput("Colombo", "Kandy", new BigDecimal("2"), SizeClassCode.M,
                ServiceLevel.STANDARD, BigDecimal.ZERO, null, null, null, origin, destination, null, handling);
    }

    private static QuotePricingInput sameDay(String weightKg, VehicleType vehicle) {
        return new QuotePricingInput("Colombo", "Kandy", new BigDecimal(weightKg), SizeClassCode.M,
                ServiceLevel.SAME_DAY, BigDecimal.ZERO, null, null, null, null, null, vehicle, List.of());
    }

    private static LaneDistance km(String kilometres) {
        return new LaneDistance(new BigDecimal(kilometres), DistanceSource.ROUTED);
    }

    private static String codeOf(Throwable error) {
        return ((BusinessRuleViolationException) error).code();
    }

    @Test
    void picksTheBandTheLaneFallsInAndReportsItsTransitTime() {
        PreparedQuote quote = service.prepare(standard(null, null, List.of()), TODAY, km("115"), null);

        assertThat(quote.breakdown().lines()).anySatisfy(line -> {
            assertThat(line.rule()).isEqualTo(PricingRule.LINE_HAUL);
            assertThat(line.amount()).isEqualByComparingTo("150.00");
        });
        assertThat(quote.estimatedTransitDays()).isEqualTo(2);
        assertThat(quote.vehicleType()).isNull();
    }

    @Test
    void anExpressParcelReportsTheExpressTransitTime() {
        QuotePricingInput express = new QuotePricingInput("Colombo", "Kandy", new BigDecimal("2"), SizeClassCode.M,
                ServiceLevel.EXPRESS, BigDecimal.ZERO);

        assertThat(service.prepare(express, TODAY, km("115"), null).estimatedTransitDays()).isEqualTo(1);
    }

    @Test
    void refusesALaneNoBandCoversRatherThanPricingItWithoutLineHaul() {
        when(bands.findBandsInForce(any())).thenReturn(BANDS.subList(0, 2));

        assertThatThrownBy(() -> service.prepare(standard(null, null, List.of()), TODAY, km("400"), null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("NO_DISTANCE_BAND"));
    }

    @Test
    void refusesOverlappingBandsAsAConfigurationError() {
        when(bands.findBandsInForce(any())).thenReturn(List.of(BANDS.get(1),
                new DistanceBand(UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("200"),
                        new BigDecimal("1.00"), 2, 1)));

        assertThatThrownBy(() -> service.prepare(standard(null, null, List.of()), TODAY, km("120"), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlap");
    }

    @Test
    void refusesToPriceLineHaulWithoutADistance() {
        assertThatThrownBy(() -> service.prepare(standard(null, null, List.of()), TODAY, null, null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("DISTANCE_UNAVAILABLE"));
    }

    @Test
    void withNoBandsConfiguredThereIsNoLineHaulAndNoTransitTime() {
        when(bands.findBandsInForce(any())).thenReturn(List.of());

        PreparedQuote quote = service.prepare(standard(null, null, List.of()), TODAY, null, null);

        assertThat(quote.estimatedTransitDays()).isNull();
        assertThat(quote.breakdown().lines()).noneMatch(line -> line.rule() == PricingRule.LINE_HAUL);
    }

    @Test
    void chargesARepeatedHandlingOptionOnceAndListsThemInConfiguredOrder() {
        PreparedQuote quote = service.prepare(standard(null, null, List.of("signature", "FRAGILE", "FRAGILE")),
                TODAY, km("10"), null);

        assertThat(quote.specialHandling()).containsExactly("FRAGILE", "SIGNATURE");
        assertThat(quote.breakdown().lines()).anySatisfy(line -> {
            assertThat(line.rule()).isEqualTo(PricingRule.SPECIAL_HANDLING);
            assertThat(line.amount()).isEqualByComparingTo("350.00");
        });
    }

    @Test
    void refusesAHandlingOptionThatIsNotOffered() {
        assertThatThrownBy(() -> service.prepare(standard(null, null, List.of("FRAGILE", "EXPLOSIVE")),
                TODAY, km("10"), null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("UNKNOWN_SPECIAL_HANDLING"));
    }

    @Test
    void refusesMoreHandlingOptionsThanTheContractAllows() {
        assertThatThrownBy(() -> service.prepare(standard(null, null, List.of("A", "B", "C", "D", "E", "F")),
                TODAY, km("10"), null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("TOO_MANY_SPECIAL_HANDLING"));
    }

    @Test
    void acceptsMapPointsInsideTheirDistricts() {
        PreparedQuote quote = service.prepare(standard(FORT, KANDY_TOWN, List.of()), TODAY, km("115"), null);

        assertThat(quote.fromZone()).isEqualTo(WP);
    }

    @Test
    void refusesAPickupPointOutsideTheDistrictChosen() {
        assertThatThrownBy(() -> service.prepare(standard(KANDY_TOWN, null, List.of()), TODAY, km("115"), null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("LOCATION_DISTRICT_MISMATCH"));
        verify(rateCards, never()).select(any(), any(), any());
    }

    @Test
    void refusesAPointOutsideEveryDistrict() {
        GeoPoint atSea = GeoPoint.of("6.5", "79.0");
        when(locator.districtAt(atSea)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(standard(null, atSea, List.of()), TODAY, km("115"), null))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("OUTSIDE_COVERAGE"));
    }

    @Test
    void pricesASameDayJobForTheBikeByDefault() {
        PreparedQuote quote = service.prepare(sameDay("2", null), TODAY, km("10"), PER_KM);

        assertThat(quote.vehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(quote.estimatedTransitDays()).isNull();
    }

    @Test
    void refusesASameDayJobTooHeavyForTheVehicle() {
        assertThatThrownBy(() -> service.prepare(sameDay("12", VehicleType.BIKE), TODAY, km("10"), PER_KM))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("SAME_DAY_TOO_HEAVY"));
    }

    @Test
    void refusesASameDayJobBeyondTheVehiclesRangeButAVanCanTakeIt() {
        assertThatThrownBy(() -> service.prepare(sameDay("2", VehicleType.BIKE), TODAY, km("60"), PER_KM))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("SAME_DAY_OUT_OF_RANGE"));

        assertThat(service.prepare(sameDay("2", VehicleType.VAN), TODAY, km("60"), PER_KM).vehicleType())
                .isEqualTo(VehicleType.VAN);
    }

    @Test
    void refusesAVehicleThatIsNotOffered() {
        when(vehicles.findActive(VehicleType.VAN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(sameDay("2", VehicleType.VAN), TODAY, km("10"), PER_KM))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo("VEHICLE_NOT_OFFERED"));
    }

    @Test
    void thePricingVersionFollowsTheConfigurationNotTheRoute() {
        String near = service.prepare(standard(null, null, List.of()), TODAY, km("31"), null).pricingVersion();
        String far = service.prepare(standard(null, null, List.of()), TODAY, km("149"), null).pricingVersion();
        String withExtra = service.prepare(standard(null, null, List.of("FRAGILE")), TODAY, km("31"), null)
                .pricingVersion();

        // Same band, same tariff: same version, whatever the exact distance.
        assertThat(near).hasSize(12).isEqualTo(far);
        assertThat(withExtra).isNotEqualTo(near);
    }
}
