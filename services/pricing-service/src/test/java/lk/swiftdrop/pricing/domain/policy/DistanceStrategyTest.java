package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceStrategyTest {

    /** Flag fall 300, 55 a kilometre, nothing under three kilometres. */
    private static final DistanceRate RATE = new DistanceRate(new BigDecimal("300.00"),
            new BigDecimal("55.00"), new BigDecimal("3.00"), new BigDecimal("1.300"));

    private static PricingContext context(ServiceLevel level, String km, DistanceRate rate) {
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("WP"),
                new BigDecimal("250.00"), new BigDecimal("40.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.M, new BigDecimal("5"),
                Dimensions.of("40", "30", "20"), BigDecimal.ONE);
        LaneDistance distance = km == null ? null
                : new LaneDistance(new BigDecimal(km), DistanceSource.ROUTED);
        return new PricingContext(
                new PricingContext.Parcel(BigDecimal.ONE, BigDecimal.ONE, size, level, BigDecimal.ZERO),
                new PricingContext.Rates(card, List.of(), null, null, List.of(), null, rate, distance));
    }

    private static BigDecimal lineOf(FeeBreakdown breakdown, PricingRule rule) {
        return breakdown.lines().stream().filter(l -> l.rule() == rule)
                .map(FeeBreakdown.Line::amount).findFirst().orElse(null);
    }

    @Test
    void chargesFlagFallPlusTheDistanceTravelled() {
        FeeBreakdown result = new DistanceStrategy()
                .price(context(ServiceLevel.SAME_DAY, "12", RATE), FeeBreakdown.empty());

        assertThat(lineOf(result, PricingRule.DISTANCE)).isEqualByComparingTo("960.00");
    }

    @Test
    void aJobAcrossTheStreetIsShortNotFree() {
        // 0.4 km is billed at the three kilometre minimum.
        assertThat(lineOf(new DistanceStrategy()
                .price(context(ServiceLevel.SAME_DAY, "0.4", RATE), FeeBreakdown.empty()),
                PricingRule.DISTANCE)).isEqualByComparingTo("465.00");
        // At the minimum exactly, the same answer.
        assertThat(lineOf(new DistanceStrategy()
                .price(context(ServiceLevel.SAME_DAY, "3", RATE), FeeBreakdown.empty()),
                PricingRule.DISTANCE)).isEqualByComparingTo("465.00");
    }

    @Test
    void addsNothingWhenThereIsNothingToMeasureWith() {
        assertThat(new DistanceStrategy()
                .price(context(ServiceLevel.SAME_DAY, null, RATE), FeeBreakdown.empty()).lines())
                .isEmpty();
        assertThat(new DistanceStrategy()
                .price(context(ServiceLevel.SAME_DAY, "12", null), FeeBreakdown.empty()).lines())
                .isEmpty();
    }

    @Test
    void sameDayIsPricedByDistanceAndEverythingElseByZone() {
        FeeBreakdown sameDay = new LaneChargeStrategy()
                .price(context(ServiceLevel.SAME_DAY, "12", RATE), FeeBreakdown.empty());
        FeeBreakdown standard = new LaneChargeStrategy()
                .price(context(ServiceLevel.STANDARD, "12", RATE), FeeBreakdown.empty());
        FeeBreakdown express = new LaneChargeStrategy()
                .price(context(ServiceLevel.EXPRESS, "12", RATE), FeeBreakdown.empty());

        assertThat(sameDay.lines()).extracting(FeeBreakdown.Line::rule)
                .containsExactly(PricingRule.DISTANCE);
        // The zone-priced levels ignore the distance entirely and read the card.
        assertThat(standard.lines()).extracting(FeeBreakdown.Line::rule)
                .containsExactly(PricingRule.BASE);
        assertThat(standard.fee()).isEqualByComparingTo("250.00");
        assertThat(express.fee()).isEqualByComparingTo("250.00");
    }
}
