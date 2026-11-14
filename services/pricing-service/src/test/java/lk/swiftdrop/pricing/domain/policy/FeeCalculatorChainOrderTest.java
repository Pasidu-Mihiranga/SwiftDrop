package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.InsuranceBand;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one convention the whole breakdown rests on: a rule's position in
 * {@link PricingRule} is the position its line appears in.
 *
 * <p>With seven rules compounding, that is no longer obvious from reading the nesting in
 * {@code FeeCalculator}, and getting it wrong would reorder a customer's itemised bill without
 * changing the total, so nothing else would fail.
 */
class FeeCalculatorChainOrderTest {

    /** A quote that triggers every rule at once. */
    private static PricingContext everything() {
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("NP"),
                new BigDecimal("475.00"), new BigDecimal("78.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.L, new BigDecimal("15"),
                Dimensions.of("60", "40", "30"), new BigDecimal("1.350"));
        return new PricingContext(
                new PricingContext.Parcel(new BigDecimal("3"), new BigDecimal("3"), size,
                        ServiceLevel.EXPRESS, new BigDecimal("2000"), new BigDecimal("30000")),
                new PricingContext.Rates(card, List.of(),
                        new RemoteAreaSurcharge("Jaffna", new BigDecimal("250.00")),
                        new RemoteAreaSurcharge("Mannar", new BigDecimal("400.00")),
                        List.of(new InsuranceBand(null, new BigDecimal("100"), new BigDecimal("0.01")))));
    }

    @Test
    void everyRuleContributesExactlyOneLineInEnumOrder() {
        FeeBreakdown result = new FeeCalculator().calculate(everything());

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule)
                .containsExactly(PricingRule.BASE, PricingRule.WEIGHT, PricingRule.SIZE,
                        PricingRule.EXPRESS, PricingRule.REMOTE_AREA, PricingRule.INSURANCE,
                        PricingRule.COD);
    }

    @Test
    void theLinesAreSortedByTheRuleTheyBelongTo() {
        List<PricingRule> emitted = new FeeCalculator().calculate(everything()).lines().stream()
                .map(FeeBreakdown.Line::rule).toList();

        assertThat(emitted).isSortedAccordingTo(Comparator.comparingInt(PricingRule::ordinal));
    }

    @Test
    void whateverTheRulesDidTheLinesStillAddUpToWhatIsCharged() {
        FeeBreakdown result = new FeeCalculator().calculate(everything());

        assertThat(result.lines().stream().map(FeeBreakdown.Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.fee());
    }
}
