package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
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
    void movesADistrictToAnotherZoneWithoutCreatingADuplicate() {
        districts.assign("Kandy", ZoneCode.of("UP"));

        assertThat(districts.findActiveZoneCodeByDistrict("Kandy")).contains(ZoneCode.of("UP"));
        assertThat(districts.findAll()).hasSize(25);
    }

    @Test
    void doesNotAssignADistrictToAZoneThatDoesNotExist() {
        assertThat(districts.assign("Kandy", ZoneCode.of("ZZ"))).isEmpty();
        assertThat(districts.findActiveZoneCodeByDistrict("Kandy")).contains(ZoneCode.of("CP"));
    }

    @Test
    void persistsANewZone() {
        Zone saved = zones.save(Zone.newZone(ZoneCode.of("TEST-A"), "Test zone", true));

        assertThat(saved.id()).isNotNull();
        assertThat(zones.existsByCode(ZoneCode.of("TEST-A"))).isTrue();
        assertThat(zones.findById(saved.id())).isPresent();
        assertThat(saved.districts()).isEmpty();
    }

    @Test
    void theDatabaseRefusesASecondZoneWithTheSameCode() {
        zones.save(Zone.newZone(ZoneCode.of("TEST-B"), "First", true));
        zones.save(Zone.newZone(ZoneCode.of("TEST-B"), "Duplicate", true));

        // The unique constraint is enforced on write, so the breach surfaces at flush time.
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void updatesAZoneWithoutChangingItsCode() {
        Zone central = zones.findByCode(ZoneCode.of("CP")).orElseThrow();

        Zone renamed = zones.save(central.with("Central Province", true));

        assertThat(renamed.code()).isEqualTo(ZoneCode.of("CP"));
        assertThat(renamed.name()).isEqualTo("Central Province");
        assertThat(renamed.districts()).contains("Kandy");
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
