package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    void calculatesAllRulesInContractOrderAndReconcilesToTheTotal() {
        FeeBreakdown result = calculator.calculate(card("475.00", "78.00", "1.500", "0.0150"),
                size("1.25", "100"), money("2.1"), ServiceLevel.EXPRESS, money("1000"));

        assertThat(result.lines()).containsExactly(
                line(PricingRule.BASE, "475.00"),
                line(PricingRule.WEIGHT, "234.00"),
                line(PricingRule.SIZE, "177.25"),
                line(PricingRule.EXPRESS, "443.13"),
                line(PricingRule.COD, "15.00"));
        assertThat(result.fee()).isEqualByComparingTo("1344.38");
        assertThat(result.lines().stream().map(FeeBreakdown.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(result.fee());
    }

    @Test
    void omitsRulesWithNoChargeAndDoesNotApplyExpressToStandardService() {
        FeeBreakdown result = calculator.calculate(card("350", "60", "1.5", "0.015"),
                size("1", "5"), money("2"), ServiceLevel.STANDARD, BigDecimal.ZERO);

        assertThat(result.lines()).containsExactly(
                line(PricingRule.BASE, "350.00"), line(PricingRule.WEIGHT, "120.00"));
        assertThat(result.fee()).isEqualByComparingTo("470.00");
    }

    @Test
    void roundsEachLineBeforeUsingItInLaterRules() {
        FeeBreakdown result = calculator.calculate(card("0.005", "0.005", "1.5", "0.005"),
                size("1.5", "10"), money("1.1"), ServiceLevel.EXPRESS, BigDecimal.ONE);

        assertThat(result.lines()).containsExactly(
                line(PricingRule.BASE, "0.01"), line(PricingRule.WEIGHT, "0.01"),
                line(PricingRule.SIZE, "0.01"), line(PricingRule.EXPRESS, "0.02"),
                line(PricingRule.COD, "0.01"));
        assertThat(result.fee()).isEqualByComparingTo("0.06");
    }

    @Test
    void acceptsExactWeightLimitButRejectsInvalidAmountsAndExcessWeight() {
        RateCard card = card("1", "1", "1", "0");
        SizeClassRate size = size("1", "2");

        assertThat(calculator.calculate(card, size, money("2"), ServiceLevel.STANDARD, BigDecimal.ZERO)
                .fee()).isEqualByComparingTo("3.00");
        assertThatThrownBy(() -> calculator.calculate(card, size, BigDecimal.ZERO,
                ServiceLevel.STANDARD, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(card, size, money("2.01"),
                ServiceLevel.STANDARD, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(card, size, BigDecimal.ONE,
                ServiceLevel.STANDARD, money("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroConfiguredFeesProduceAnEmptyBreakdownAndZeroTotal() {
        FeeBreakdown result = calculator.calculate(card("0", "0", "1", "0"),
                size("1", "5"), BigDecimal.ONE, ServiceLevel.EXPRESS, BigDecimal.ONE);

        assertThat(result.lines()).isEqualTo(List.of());
        assertThat(result.fee()).isEqualByComparingTo("0.00");
    }

    private static FeeBreakdown.Line line(PricingRule rule, String amount) {
        return new FeeBreakdown.Line(rule, money(amount));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static SizeClassRate size(String multiplier, String maxWeight) {
        return new SizeClassRate(SizeClassCode.M, money(maxWeight), null, money(multiplier));
    }

    private static RateCard card(String base, String perKg, String express, String codPct) {
        return new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("CP"),
                money(base), money(perKg), money(express), money(codPct), LocalDate.of(2026, 1, 1), null);
    }
}
