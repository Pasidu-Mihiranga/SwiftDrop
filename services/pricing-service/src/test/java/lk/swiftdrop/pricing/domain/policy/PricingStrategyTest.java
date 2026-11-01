package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each rule is exercised on its own, which is the point of splitting them into strategies and
 * decorators: a surcharge can be reasoned about without running the whole quote pipeline.
 */
class PricingStrategyTest {

    @Test
    void theZoneMatrixChargesTheLaneBaseFee() {
        FeeBreakdown result = new ZoneMatrixStrategy().price(context("400", "0", "1", "0",
                "1", "10", "1", ServiceLevel.STANDARD, "0"), FeeBreakdown.empty());

        assertThat(result.lines()).containsExactly(new FeeBreakdown.Line(PricingRule.BASE, money("400.00")));
        assertThat(result.fee()).isEqualByComparingTo("400.00");
    }

    @Test
    void weightIsBilledInWholeKilogramsRoundedUp() {
        FeeBreakdown result = new WeightStrategy().price(context("0", "60", "1", "0",
                "1", "10", "2.1", ServiceLevel.STANDARD, "0"), FeeBreakdown.empty());

        assertThat(result.fee()).isEqualByComparingTo("180.00");
        assertThat(WeightStrategy.chargeableKilograms(money("2.1"))).isEqualByComparingTo("3");
        assertThat(WeightStrategy.chargeableKilograms(money("3.0"))).isEqualByComparingTo("3");
    }

    @Test
    void theSizeSurchargeIsAProportionOfWhatTheWrappedStrategyCharged() {
        PricingStrategy sized = new SizeSurchargeDecorator(new ZoneMatrixStrategy());

        FeeBreakdown result = sized.price(context("400", "0", "1", "0",
                "1.25", "10", "1", ServiceLevel.STANDARD, "0"), FeeBreakdown.empty());

        assertThat(result.lines()).containsExactly(
                new FeeBreakdown.Line(PricingRule.BASE, money("400.00")),
                new FeeBreakdown.Line(PricingRule.SIZE, money("100.00")));
        assertThat(result.fee()).isEqualByComparingTo("500.00");
    }

    @Test
    void aSizeClassWithNoMultiplierAddsNoLine() {
        FeeBreakdown result = new SizeSurchargeDecorator(new ZoneMatrixStrategy())
                .price(context("400", "0", "1", "0", "1", "10", "1", ServiceLevel.STANDARD, "0"),
                        FeeBreakdown.empty());

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule).containsExactly(PricingRule.BASE);
    }

    @Test
    void expressChargesOnTheSizeInclusiveTotalAndIsSkippedForStandardService() {
        PricingStrategy chain = new ExpressSurchargeDecorator(
                new SizeSurchargeDecorator(new ZoneMatrixStrategy()));

        FeeBreakdown express = chain.price(context("400", "0", "1.5", "0",
                "1.25", "10", "1", ServiceLevel.EXPRESS, "0"), FeeBreakdown.empty());
        FeeBreakdown standard = chain.price(context("400", "0", "1.5", "0",
                "1.25", "10", "1", ServiceLevel.STANDARD, "0"), FeeBreakdown.empty());

        // Base 400 plus size 100 is 500, so express at 1.5 adds half of 500.
        assertThat(express.lines()).last().isEqualTo(new FeeBreakdown.Line(PricingRule.EXPRESS, money("250.00")));
        assertThat(express.fee()).isEqualByComparingTo("750.00");
        assertThat(standard.lines()).extracting(FeeBreakdown.Line::rule).doesNotContain(PricingRule.EXPRESS);
    }

    @Test
    void theCodFeeIsTakenFromTheCashCollectedNotFromTheDeliveryFee() {
        PricingStrategy chain = new CodFeeDecorator(new ZoneMatrixStrategy());

        FeeBreakdown withCod = chain.price(context("400", "0", "1", "0.015",
                "1", "10", "1", ServiceLevel.STANDARD, "2000"), FeeBreakdown.empty());
        FeeBreakdown withoutCod = chain.price(context("400", "0", "1", "0.015",
                "1", "10", "1", ServiceLevel.STANDARD, "0"), FeeBreakdown.empty());

        assertThat(withCod.lines()).last().isEqualTo(new FeeBreakdown.Line(PricingRule.COD, money("30.00")));
        assertThat(withoutCod.lines()).extracting(FeeBreakdown.Line::rule).doesNotContain(PricingRule.COD);
    }

    @Test
    void decoratorsCanBeReorderedWhichIsWhyTheOrderIsFixedInOnePlace() {
        PricingContext context = context("400", "0", "1.5", "0",
                "1.25", "10", "1", ServiceLevel.EXPRESS, "0");

        BigDecimal sizeThenExpress = new ExpressSurchargeDecorator(
                new SizeSurchargeDecorator(new ZoneMatrixStrategy())).price(context, FeeBreakdown.empty()).fee();
        BigDecimal expressThenSize = new SizeSurchargeDecorator(
                new ExpressSurchargeDecorator(new ZoneMatrixStrategy())).price(context, FeeBreakdown.empty()).fee();

        assertThat(sizeThenExpress).isEqualByComparingTo("750.00");
        assertThat(expressThenSize).isEqualByComparingTo("750.00");
        assertThat(sizeThenExpress).isEqualByComparingTo(expressThenSize);
    }

    @Test
    void aCompositeRunsItsStrategiesInOrderAndAnEmptyOneChargesNothing() {
        PricingContext context = context("400", "60", "1", "0",
                "1", "10", "2", ServiceLevel.STANDARD, "0");

        FeeBreakdown both = new CompositePricingStrategy(new ZoneMatrixStrategy(), new WeightStrategy())
                .price(context, FeeBreakdown.empty());
        FeeBreakdown none = new CompositePricingStrategy().price(context, FeeBreakdown.empty());

        assertThat(both.lines()).extracting(FeeBreakdown.Line::rule)
                .containsExactly(PricingRule.BASE, PricingRule.WEIGHT);
        assertThat(both.fee()).isEqualByComparingTo("520.00");
        assertThat(none.lines()).isEmpty();
        assertThat(none.fee()).isEqualByComparingTo("0.00");
    }

    @Test
    void aDecoratorRefusesToWrapNothing() {
        assertThatThrownBy(() -> new SizeSurchargeDecorator(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theCalculatorAcceptsAnAlternativeRuleSet() {
        FeeBreakdown result = new FeeCalculator(new ZoneMatrixStrategy())
                .calculate(card("400", "60", "1.5", "0.015"), size("1.25", "10"),
                        money("2"), ServiceLevel.EXPRESS, money("1000"));

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule).containsExactly(PricingRule.BASE);
        assertThat(result.fee()).isEqualByComparingTo("400.00");
    }

    private static PricingContext context(String base, String perKg, String express, String codPct,
                                          String multiplier, String maxWeight, String weightKg,
                                          ServiceLevel serviceLevel, String codAmount) {
        BigDecimal weight = money(weightKg);
        return new PricingContext(
                new PricingContext.Parcel(weight, weight, size(multiplier, maxWeight), serviceLevel,
                        money(codAmount)),
                new PricingContext.Rates(card(base, perKg, express, codPct)));
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static SizeClassRate size(String multiplier, String maxWeight) {
        return new SizeClassRate(SizeClassCode.M, money(maxWeight), Dimensions.of("40", "30", "20"), money(multiplier));
    }

    private static RateCard card(String base, String perKg, String express, String codPct) {
        return new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("CP"),
                money(base), money(perKg), money(express), money(codPct), LocalDate.of(2026, 1, 1), null);
    }
}
