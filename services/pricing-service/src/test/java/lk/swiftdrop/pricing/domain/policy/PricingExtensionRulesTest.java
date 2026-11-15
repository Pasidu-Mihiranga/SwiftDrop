package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Line haul, minimum charge, fuel, special handling, tax and the same-day vehicle, priced through
 * the real chain. Every expected figure below is worked by hand in the comment beside it.
 */
class PricingExtensionRulesTest {

    private static final FeeCalculator CALCULATOR = new FeeCalculator();

    private static final PricingSettings SETTINGS = new PricingSettings(UUID.randomUUID(),
            new BigDecimal("0.05"), new BigDecimal("0.18"), new BigDecimal("350.00"));
    private static final DistanceBand BAND_100_200 = new DistanceBand(UUID.randomUUID(), new BigDecimal("100"),
            new BigDecimal("200"), new BigDecimal("300.00"), 2, 1);
    private static final SpecialHandlingOption FRAGILE =
            new SpecialHandlingOption("FRAGILE", "Fragile", new BigDecimal("250.00"));
    private static final SpecialHandlingOption SIGNATURE =
            new SpecialHandlingOption("SIGNATURE", "Signature required", new BigDecimal("100.00"));
    private static final SizeClassRate MEDIUM = new SizeClassRate(SizeClassCode.M, new BigDecimal("5"),
            Dimensions.of("40", "30", "20"), new BigDecimal("1.000"));

    private static RateCard card(String baseFee, String perKgFee) {
        return new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("NP"), new BigDecimal(baseFee),
                new BigDecimal(perKgFee), new BigDecimal("1.500"), new BigDecimal("0.0150"),
                LocalDate.of(2026, 1, 1), null);
    }

    private static PricingContext.Parcel parcel(ServiceLevel level) {
        return new PricingContext.Parcel(BigDecimal.ONE, BigDecimal.ONE, MEDIUM, level, BigDecimal.ZERO);
    }

    private static PricingContext.Rates rates(RateCard card, DistanceBand band, PricingSettings settings,
                                              List<SpecialHandlingOption> handling) {
        return new PricingContext.Rates(card, List.of(), null, null, List.of(), null, null, null, band, settings,
                handling, null);
    }

    private static BigDecimal lineOf(FeeBreakdown breakdown, PricingRule rule) {
        return breakdown.lines().stream().filter(line -> line.rule() == rule)
                .map(FeeBreakdown.Line::amount).findFirst().orElse(null);
    }

    @Test
    void pricesAStandardParcelThroughEveryNewRuleInTheContractOrder() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.STANDARD),
                rates(card("475.00", "78.00"), BAND_100_200, SETTINGS, List.of(FRAGILE))));

        // BASE 475 + LINE_HAUL 300 + WEIGHT 78 = 853; above the 350 floor, so no top-up.
        // FUEL 853 x 5% = 42.65; FRAGILE 250; pre-tax 1145.65; TAX x 18% = 206.217 -> 206.22.
        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule).containsExactly(
                PricingRule.BASE, PricingRule.LINE_HAUL, PricingRule.WEIGHT, PricingRule.FUEL,
                PricingRule.SPECIAL_HANDLING, PricingRule.TAX);
        assertThat(lineOf(result, PricingRule.LINE_HAUL)).isEqualByComparingTo("300.00");
        assertThat(lineOf(result, PricingRule.FUEL)).isEqualByComparingTo("42.65");
        assertThat(lineOf(result, PricingRule.SPECIAL_HANDLING)).isEqualByComparingTo("250.00");
        assertThat(lineOf(result, PricingRule.TAX)).isEqualByComparingTo("206.22");
        assertThat(result.fee()).isEqualByComparingTo("1351.87");
    }

    @Test
    void chargesFuelOnTheExpressInclusiveTransportCharge() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.EXPRESS),
                rates(card("475.00", "78.00"), BAND_100_200, SETTINGS, List.of())));

        // 853 transport; EXPRESS 853 x 0.5 = 426.50 -> 1279.50; FUEL x 5% = 63.975 -> 63.98;
        // pre-tax 1343.48; TAX x 18% = 241.8264 -> 241.83; total 1585.31.
        assertThat(lineOf(result, PricingRule.EXPRESS)).isEqualByComparingTo("426.50");
        assertThat(lineOf(result, PricingRule.FUEL)).isEqualByComparingTo("63.98");
        assertThat(result.fee()).isEqualByComparingTo("1585.31");
    }

    @Test
    void topsAShortLightParcelUpToTheMinimumBeforeFuelAndExtras() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.STANDARD),
                rates(card("100.00", "20.00"), null, SETTINGS, List.of(SIGNATURE))));

        // BASE 100 + WEIGHT 20 = 120; MINIMUM tops up 230 to 350; FUEL 17.50 -> 367.50;
        // SIGNATURE 100 is bought on top of the floor -> 467.50; TAX 84.15; total 551.65.
        assertThat(lineOf(result, PricingRule.MINIMUM_CHARGE)).isEqualByComparingTo("230.00");
        assertThat(lineOf(result, PricingRule.FUEL)).isEqualByComparingTo("17.50");
        assertThat(lineOf(result, PricingRule.TAX)).isEqualByComparingTo("84.15");
        assertThat(result.fee()).isEqualByComparingTo("551.65");
    }

    @Test
    void sumsSeveralHandlingOptionsIntoOneLine() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.STANDARD),
                rates(card("475.00", "78.00"), null, null, List.of(FRAGILE, SIGNATURE))));

        assertThat(lineOf(result, PricingRule.SPECIAL_HANDLING)).isEqualByComparingTo("350.00");
    }

    @Test
    void theBreakdownStillSumsExactlyToTheFee() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.EXPRESS),
                rates(card("333.33", "77.77"), BAND_100_200, SETTINGS, List.of(FRAGILE, SIGNATURE))));

        BigDecimal sum = result.lines().stream().map(FeeBreakdown.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(result.fee());
    }

    @Test
    void withNothingNewConfiguredTheFeeIsWhatItWasBefore() {
        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.STANDARD),
                new PricingContext.Rates(card("475.00", "78.00"))));

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule)
                .containsExactly(PricingRule.BASE, PricingRule.WEIGHT);
        assertThat(result.fee()).isEqualByComparingTo("553.00");
    }

    @Test
    void sameDayWorkPaysPerKilometreScaledByTheVehicleAndNeverLineHaul() {
        SameDayVehicle van = new SameDayVehicle(VehicleType.VAN, new BigDecimal("100"), new BigDecimal("120"),
                new BigDecimal("1.800"));
        PricingContext.Rates rates = new PricingContext.Rates(card("475.00", "78.00"), List.of(), null, null,
                List.of(), null,
                new DistanceRate(new BigDecimal("300.00"), new BigDecimal("55.00"), new BigDecimal("3"),
                        new BigDecimal("1.3")),
                new LaneDistance(new BigDecimal("10"), DistanceSource.ROUTED), BAND_100_200, null, List.of(), van);

        FeeBreakdown result = CALCULATOR.calculate(new PricingContext(parcel(ServiceLevel.SAME_DAY), rates));

        // (300 flag fall + 55 x 10 km) x 1.8 for a van = 1530.00, and no band charge on top.
        assertThat(lineOf(result, PricingRule.DISTANCE)).isEqualByComparingTo("1530.00");
        assertThat(lineOf(result, PricingRule.LINE_HAUL)).isNull();
        assertThat(lineOf(result, PricingRule.BASE)).isNull();
    }

    @Test
    void bandsAreHalfOpenSoABoundaryBelongsToTheBandItStarts() {
        assertThat(BAND_100_200.contains(new BigDecimal("100"))).isTrue();
        assertThat(BAND_100_200.contains(new BigDecimal("199.99"))).isTrue();
        assertThat(BAND_100_200.contains(new BigDecimal("200"))).isFalse();
        assertThat(BAND_100_200.contains(new BigDecimal("99.99"))).isFalse();
        assertThat(BAND_100_200.transitDays(ServiceLevel.STANDARD)).isEqualTo(2);
        assertThat(BAND_100_200.transitDays(ServiceLevel.EXPRESS)).isEqualTo(1);
    }

    @Test
    void refusesConfigurationThatCouldNeverBeRight() {
        assertThatThrownBy(() -> new PricingSettings(null, new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingSettings(null, BigDecimal.ZERO, new BigDecimal("-0.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistanceBand(null, new BigDecimal("50"), new BigDecimal("50"),
                BigDecimal.ZERO, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistanceBand(null, BigDecimal.ZERO, null, BigDecimal.ZERO, 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SameDayVehicle(VehicleType.BIKE, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpecialHandlingOption("X", "X", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
