package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.DistanceBandRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingRepository;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the Flyway seed data and the persistence adapters against a real Postgres: the reviewed
 * zone, district, size-class and rate-card reference data has to be exactly what the pricing
 * engine will read in production.
 */
@Testcontainers
@SpringBootTest
@Transactional
class PricingReferenceDataIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ZoneRepository zones;
    @Autowired
    private DistrictAssignmentRepository districts;
    @Autowired
    private SizeClassRepository sizeClasses;
    @Autowired
    private RateCardRepository rateCards;
    @Autowired
    private VolumetricDivisorRepository divisors;
    @Autowired
    private WeightTierRepository weightTiers;
    @Autowired
    private PricingSettingsRepository settings;
    @Autowired
    private DistanceBandRepository distanceBands;
    @Autowired
    private SpecialHandlingRepository specialHandling;
    @Autowired
    private SameDayVehicleRepository vehicles;

    @Test
    void seedsTheNineProvinceZonesAllActiveAndSortedByCode() {
        List<Zone> active = zones.findActive();

        assertThat(active).hasSize(9);
        assertThat(active).allMatch(Zone::active);
        assertThat(active.stream().map(zone -> zone.code().value()))
                .containsExactly("CP", "EP", "NCP", "NP", "NWP", "SGP", "SP", "UP", "WP");
    }

    @Test
    void mapsAllTwentyFiveDistrictsExactlyOnce() {
        List<DistrictAssignment> assignments = districts.findAll();

        assertThat(assignments).hasSize(25);
        assertThat(assignments.stream().map(DistrictAssignment::district)).doesNotHaveDuplicates();
        assertThat(zones.findActive().stream().mapToInt(zone -> zone.districts().size()).sum()).isEqualTo(25);
    }

    @Test
    void resolvesADistrictToItsZoneIgnoringCase() {
        assertThat(districts.findActiveZoneCodeByDistrict("Kandy")).contains(ZoneCode.of("CP"));
        assertThat(districts.findActiveZoneCodeByDistrict("colombo")).contains(ZoneCode.of("WP"));
        assertThat(districts.findActiveZoneCodeByDistrict(" Jaffna ")).contains(ZoneCode.of("NP"));
    }

    @Test
    void doesNotResolveADistrictThatIsNotServiced() {
        assertThat(districts.findActiveZoneCodeByDistrict("Chennai")).isEmpty();
        assertThat(districts.findByDistrict("Chennai")).isEmpty();
    }

    @Test
    void seedsTheFourSizeClassesWithNonDiscountingMultipliers() {
        assertThat(sizeClasses.findAll()).hasSize(4);
        assertThat(sizeClasses.findByCode(SizeClassCode.S)).isPresent()
                .get().extracting(rate -> rate.multiplier().compareTo(BigDecimal.ONE)).isEqualTo(0);
        assertThat(sizeClasses.findByCode(SizeClassCode.XL)).isPresent()
                .get().extracting(rate -> rate.multiplier().compareTo(BigDecimal.ONE) > 0).isEqualTo(true);
    }

    @Test
    void seedsOneRateCardForEveryOrderedZonePair() {
        assertThat(rateCards.findAll()).hasSize(81);
    }

    @Test
    void findsExactlyOneRateCardInForceForAZonePair() {
        List<RateCard> inForce = rateCards.findInForce(ZoneCode.of("WP"), ZoneCode.of("CP"), TODAY);

        assertThat(inForce).hasSize(1);
        assertThat(inForce.getFirst().baseFee()).isPositive();
        assertThat(inForce.getFirst().isInForceOn(TODAY)).isTrue();
    }

    @Test
    void findsNoRateCardBeforeTheSeededRatesTakeEffect() {
        assertThat(rateCards.findInForce(ZoneCode.of("WP"), ZoneCode.of("CP"), LocalDate.of(2025, 12, 31)))
                .isEmpty();
    }

    @Test
    void pricesTheSameZoneLaneBelowAThreeBorderLane() {
        BigDecimal within = baseFeeFor("WP", "WP");
        BigDecimal neighbouring = baseFeeFor("WP", "SP");
        BigDecimal acrossTheIsland = baseFeeFor("WP", "NP");

        assertThat(within).isLessThan(neighbouring);
        assertThat(neighbouring).isLessThan(acrossTheIsland);
    }

    @Test
    void pricesEachLaneTheSameInBothDirections() {
        assertThat(baseFeeFor("WP", "NP")).isEqualByComparingTo(baseFeeFor("NP", "WP"));
        assertThat(baseFeeFor("CP", "SP")).isEqualByComparingTo(baseFeeFor("SP", "CP"));
    }

    @Test
    void mapsEveryDistrictToItsRealProvince() {
        Map<String, List<String>> expected = Map.of(
                "WP", List.of("Colombo", "Gampaha", "Kalutara"),
                "CP", List.of("Kandy", "Matale", "Nuwara Eliya"),
                "SP", List.of("Galle", "Matara", "Hambantota"),
                "NP", List.of("Jaffna", "Kilinochchi", "Mannar", "Mullaitivu", "Vavuniya"),
                "EP", List.of("Ampara", "Batticaloa", "Trincomalee"),
                "NWP", List.of("Kurunegala", "Puttalam"),
                "NCP", List.of("Anuradhapura", "Polonnaruwa"),
                "UP", List.of("Badulla", "Monaragala"),
                "SGP", List.of("Kegalle", "Ratnapura"));

        assertThat(expected).hasSize(9);
        expected.forEach((zone, names) -> {
            assertThat(zones.findByCode(ZoneCode.of(zone)).orElseThrow().districts())
                    .containsExactlyInAnyOrderElementsOf(names);
            names.forEach(name -> assertThat(districts.findActiveZoneCodeByDistrict(name))
                    .contains(ZoneCode.of(zone)));
        });
    }

    @Test
    void refusesToMoveADistrictToAnotherProvince() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("UPDATE district_zone SET zone_id = "
                + "(SELECT id FROM zone WHERE code = 'CP') WHERE district = 'Ampara'").executeUpdate())
                .hasStackTraceContaining("Geography is fixed master data");
    }

    @Test
    void refusesToAddADistrict() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("INSERT INTO district_zone (district, zone_id) "
                + "SELECT 'Atlantis', id FROM zone WHERE code = 'WP'").executeUpdate())
                .hasStackTraceContaining("Geography is fixed master data");
    }

    @Test
    void refusesToAddRemoveOrRenameAZone() {
        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE zone SET name = 'Somewhere else' WHERE code = 'WP'").executeUpdate())
                .hasStackTraceContaining("Geography is fixed master data");
    }

    @Test
    void refusesToSwitchAProvinceZoneOff() {
        assertThatThrownBy(() -> entityManager.createNativeQuery(
                "UPDATE zone SET active = FALSE WHERE code = 'NP'").executeUpdate())
                .hasStackTraceContaining("Geography is fixed master data");
    }

    @Test
    void aMigrationThatMeansToChangeGeographyCanSaySo() {
        entityManager.createNativeQuery("SET LOCAL swiftdrop.allow_geography_change = 'on'").executeUpdate();

        int moved = entityManager.createNativeQuery("UPDATE district_zone SET zone_id = "
                + "(SELECT id FROM zone WHERE code = 'CP') WHERE district = 'Ampara'").executeUpdate();

        // Rolled back with the test transaction, so the seed stays intact for the other tests.
        assertThat(moved).isEqualTo(1);
    }

    @Test
    void seedsOneSetOfFeeWideSettings() {
        var inForce = settings.findInForce(TODAY).orElseThrow();

        assertThat(inForce.fuelSurchargePct()).isEqualByComparingTo("0.05");
        assertThat(inForce.taxPct()).isEqualByComparingTo("0.18");
        assertThat(inForce.minimumCharge()).isEqualByComparingTo("350.00");
        assertThat(settings.findInForce(LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void seedsDistanceBandsThatCoverEveryDistanceExactlyOnce() {
        var bands = distanceBands.findBandsInForce(TODAY);

        assertThat(bands).hasSize(5);
        assertThat(bands.getFirst().minKm()).isEqualByComparingTo("0");
        assertThat(bands.getLast().maxKm()).isNull();
        for (int i = 1; i < bands.size(); i++) {
            // Contiguous: each band starts where the one before it ends, so no distance falls
            // into a gap and none is claimed twice.
            assertThat(bands.get(i).minKm()).isEqualByComparingTo(bands.get(i - 1).maxKm());
        }
        for (String km : List.of("0", "29.99", "30", "150", "299.99", "300", "1000")) {
            assertThat(bands.stream().filter(band -> band.contains(new BigDecimal(km)))).hasSize(1);
        }
    }

    @Test
    void seedsTheHandlingOptionsInDisplayOrder() {
        assertThat(specialHandling.findActive()).extracting(SpecialHandlingOption::code)
                .containsExactly("FRAGILE", "PERISHABLE", "LIQUID", "SIGNATURE");
    }

    @Test
    void seedsABikeAndAVanForSameDayWork() {
        var bike = vehicles.findActive(VehicleType.BIKE).orElseThrow();
        var van = vehicles.findActive(VehicleType.VAN).orElseThrow();

        assertThat(vehicles.findAllActive()).hasSize(2);
        assertThat(bike.maxWeightKg()).isLessThan(van.maxWeightKg());
        assertThat(bike.maxDistanceKm()).isLessThan(van.maxDistanceKm());
        assertThat(bike.rateMultiplier()).isLessThan(van.rateMultiplier());
    }

    @Test
    void closesARateCardSoItNoLongerApplies() {
        RateCard open = rateCards.findInForce(ZoneCode.of("WP"), ZoneCode.of("CP"), TODAY).getFirst();

        RateCard closed = rateCards.save(open.closedOn(TODAY));

        assertThat(closed.effectiveTo()).isEqualTo(TODAY);
        assertThat(rateCards.findInForce(ZoneCode.of("WP"), ZoneCode.of("CP"), TODAY)).isEmpty();
        assertThat(rateCards.findById(open.id())).isPresent();
    }

    @Test
    void repricesASizeClassInPlace() {
        var medium = sizeClasses.findByCode(SizeClassCode.M).orElseThrow();

        var updated = sizeClasses.save(new lk.swiftdrop.pricing.domain.model.SizeClassRate(
                SizeClassCode.M, medium.maxWeightKg(), medium.maxDimensions(), new BigDecimal("1.200")));

        assertThat(updated.multiplier()).isEqualByComparingTo("1.200");
        assertThat(sizeClasses.findAll()).hasSize(4);
    }

    private BigDecimal baseFeeFor(String fromZone, String toZone) {
        Optional<RateCard> card = rateCards.findInForce(ZoneCode.of(fromZone), ZoneCode.of(toZone), TODAY)
                .stream().findFirst();
        return card.orElseThrow().baseFee();
    }

    @Test
    void seedsOneVolumetricDivisorAtTheCourierConvention() {
        assertThat(divisors.findInForce(TODAY)).contains(new java.math.BigDecimal("5000.0"));
    }

    @Test
    void appliesNoVolumetricDivisorBeforeItTakesEffect() {
        assertThat(divisors.findInForce(java.time.LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void givesEverySizeClassGeometryThatMatchesItsPublishedLabel() {
        var classes = sizeClasses.findAll();

        assertThat(classes).hasSize(4);
        assertThat(classes).allSatisfy(rate -> {
            assertThat(rate.maxDimensions()).isNotNull();
            assertThat(rate.maxDimensionsLabel()).matches("\\d+(\\.\\d+)?x\\d+(\\.\\d+)?x\\d+(\\.\\d+)?");
        });
        assertThat(sizeClasses.findByCode(SizeClassCode.M).orElseThrow().maxDimensionsLabel())
                .isEqualTo("40x30x20");
    }

    @Test
    void seedsNoWeightBandsSoEveryLaneKeepsItsFlatRate() {
        var anyCard = rateCards.findAll().get(0);

        assertThat(weightTiers.findByRateCard(anyCard.id())).isEmpty();
    }

    @Test
    void storesWeightBandsAgainstTheCardAndReadsThemBackOpenEndedLast() {
        var card = rateCards.findAll().get(0);
        weightTiers.setForRateCard(card.id(), java.util.List.of(
                new lk.swiftdrop.pricing.domain.model.WeightTier(null, new java.math.BigDecimal("40.00")),
                new lk.swiftdrop.pricing.domain.model.WeightTier(
                        new java.math.BigDecimal("5.00"), new java.math.BigDecimal("100.00"))));

        var bands = weightTiers.findByRateCard(card.id());

        // Written open-ended first, read back open-ended last, which is the order the charge
        // walks them in.
        assertThat(bands).hasSize(2);
        assertThat(bands.get(0).upToKg()).isEqualByComparingTo("5.00");
        assertThat(bands.get(1).isOpenEnded()).isTrue();
    }
}
