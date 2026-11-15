package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.WeightTier;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TieredWeightStrategyTest {

    /** First five kilograms at 100, everything after at 40. */
    private static final List<WeightTier> BANDS = List.of(
            new WeightTier(new BigDecimal("5"), new BigDecimal("100")),
            new WeightTier(null, new BigDecimal("40")));

    private static BigDecimal charge(List<WeightTier> tiers, String weightKg) {
        FeeBreakdown result = new TieredWeightStrategy().price(context(tiers, weightKg), FeeBreakdown.empty());
        return result.lines().isEmpty() ? BigDecimal.ZERO : result.lines().get(0).amount();
    }

    private static PricingContext context(List<WeightTier> tiers, String weightKg) {
        BigDecimal weight = new BigDecimal(weightKg);
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("CP"),
                new BigDecimal("350.00"), new BigDecimal("60.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.M, new BigDecimal("100"),
                Dimensions.of("40", "30", "20"), new BigDecimal("1.000"));
        return new PricingContext(
                new PricingContext.Parcel(weight, weight, size, ServiceLevel.STANDARD, BigDecimal.ZERO),
                new PricingContext.Rates(card, tiers));
    }

    @Test
    void aLaneWithNoBandsChargesItsFlatRateForEveryKilogram() {
        // 60 per kg, ceil(2.1) = 3. The answer the service gave before bands existed.
        assertThat(charge(List.of(), "2.1")).isEqualByComparingTo("180.00");
    }

    @Test
    void bandsAreMarginalSoEachKilogramIsChargedAtItsOwnRate() {
        // 12 kg: five at 100, seven at 40.
        assertThat(charge(BANDS, "12")).isEqualByComparingTo("780.00");
        // 3 kg never leaves the first band.
        assertThat(charge(BANDS, "3")).isEqualByComparingTo("300.00");
    }

    @Test
    void thereIsNoCliffWhereOneBandEnds() {
        BigDecimal atBoundary = charge(BANDS, "5");
        BigDecimal justOver = charge(BANDS, "5.1");

        assertThat(atBoundary).isEqualByComparingTo("500.00");
        // The sixth kilogram costs 40, not another 100, and not 12 times 40.
        assertThat(justOver).isEqualByComparingTo("540.00");
        assertThat(justOver.subtract(atBoundary)).isEqualByComparingTo("40.00");
    }

    @Test
    void partKilogramsAreBilledAsWholeOnes() {
        assertThat(TieredWeightStrategy.chargeableKilograms(new BigDecimal("2.1")))
                .isEqualByComparingTo("3");
        assertThat(TieredWeightStrategy.chargeableKilograms(new BigDecimal("3.0")))
                .isEqualByComparingTo("3");
        assertThat(charge(BANDS, "0.4")).isEqualByComparingTo("100.00");
    }

    @Test
    void aSingleOpenEndedBandPricesEverythingAtThatRate() {
        List<WeightTier> flat = List.of(new WeightTier(null, new BigDecimal("25")));

        assertThat(charge(flat, "8")).isEqualByComparingTo("200.00");
    }

    @Test
    void bandsThatStopShortOfTheParcelDoNotLetTheRestTravelFree() {
        // A configuration error: no open-ended band, and a parcel heavier than the last one.
        List<WeightTier> capped = List.of(
                new WeightTier(new BigDecimal("5"), new BigDecimal("100")),
                new WeightTier(new BigDecimal("10"), new BigDecimal("40")));

        // Five at 100, five at 40, then the two kilograms past the top band at that same 40.
        assertThat(charge(capped, "12")).isEqualByComparingTo("780.00");
    }

    @Test
    void aBulkyParcelIsBandedOnWhatItDisplacesNotWhatItWeighs() {
        BigDecimal weight = new BigDecimal("3");
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("CP"),
                new BigDecimal("350.00"), new BigDecimal("60.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.L, new BigDecimal("100"),
                Dimensions.of("60", "40", "30"), new BigDecimal("1.000"));
        PricingContext bulky = new PricingContext(
                new PricingContext.Parcel(weight, new BigDecimal("14.4"), size,
                        ServiceLevel.STANDARD, BigDecimal.ZERO),
                new PricingContext.Rates(card, BANDS));

        // ceil(14.4) = 15: five at 100, ten at 40.
        assertThat(new TieredWeightStrategy().price(bulky, FeeBreakdown.empty()).lines())
                .extracting(FeeBreakdown.Line::rule).containsExactly(PricingRule.WEIGHT);
        assertThat(new TieredWeightStrategy().price(bulky, FeeBreakdown.empty()).fee())
                .isEqualByComparingTo("900.00");
    }
}
