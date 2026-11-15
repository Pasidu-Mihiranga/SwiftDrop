package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.InsuranceBand;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantDiscountDecoratorTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final ZoneCode WP = ZoneCode.of("WP");
    private static final ZoneCode CP = ZoneCode.of("CP");

    private static final PricingStrategy BASE = (context, soFar) ->
            soFar.plus(PricingRule.BASE, new BigDecimal("1000.00"));

    private static MerchantAgreement agreement(String pct, String flat, List<LaneOverride> overrides) {
        return new MerchantAgreement(UUID.randomUUID(), MERCHANT, new BigDecimal(pct),
                new BigDecimal(flat), false, false, false, overrides);
    }

    private static PricingContext context(MerchantAgreement agreement) {
        RateCard card = new RateCard(UUID.randomUUID(), WP, CP, new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"),
                LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.M, new BigDecimal("5"),
                Dimensions.of("40", "30", "20"), BigDecimal.ONE);
        return new PricingContext(
                new PricingContext.Parcel(BigDecimal.ONE, BigDecimal.ONE, size, ServiceLevel.STANDARD,
                        new BigDecimal("2000"), new BigDecimal("30000")),
                new PricingContext.Rates(card, List.of(),
                        new RemoteAreaSurcharge("Jaffna", new BigDecimal("250.00")), null,
                        List.of(new InsuranceBand(null, new BigDecimal("100"), new BigDecimal("0.01"))),
                        agreement));
    }

    private static BigDecimal lineOf(FeeBreakdown breakdown, PricingRule rule) {
        return breakdown.lines().stream().filter(l -> l.rule() == rule)
                .map(FeeBreakdown.Line::amount).findFirst().orElse(null);
    }

    @Test
    void takesThePercentageOffAsANegativeLine() {
        FeeBreakdown result = new MerchantDiscountDecorator(BASE)
                .price(context(agreement("0.15", "0", List.of())), FeeBreakdown.empty());

        assertThat(lineOf(result, PricingRule.MERCHANT_DISCOUNT)).isEqualByComparingTo("-150.00");
        assertThat(result.fee()).isEqualByComparingTo("850.00");
    }

    @Test
    void appliesTheFlatAmountAsWellAsThePercentage() {
        FeeBreakdown result = new MerchantDiscountDecorator(BASE)
                .price(context(agreement("0.10", "50", List.of())), FeeBreakdown.empty());

        assertThat(lineOf(result, PricingRule.MERCHANT_DISCOUNT)).isEqualByComparingTo("-150.00");
    }

    @Test
    void aLanesOwnRateBeatsTheAgreementsForThatLane() {
        MerchantAgreement withLane = agreement("0.10", "0",
                List.of(new LaneOverride(WP, CP, new BigDecimal("0.30"), null)));

        assertThat(lineOf(new MerchantDiscountDecorator(BASE).price(context(withLane), FeeBreakdown.empty()),
                PricingRule.MERCHANT_DISCOUNT)).isEqualByComparingTo("-300.00");
    }

    @Test
    void aLaneNegotiatedForADifferentRouteDoesNotApply() {
        MerchantAgreement elsewhere = agreement("0.10", "0",
                List.of(new LaneOverride(ZoneCode.of("SP"), ZoneCode.of("NP"), new BigDecimal("0.30"), null)));

        assertThat(lineOf(new MerchantDiscountDecorator(BASE).price(context(elsewhere), FeeBreakdown.empty()),
                PricingRule.MERCHANT_DISCOUNT)).isEqualByComparingTo("-100.00");
    }

    @Test
    void aDiscountLargerThanTheChargeMakesTheDeliveryFreeNotNegative() {
        FeeBreakdown result = new MerchantDiscountDecorator(BASE)
                .price(context(agreement("0.80", "9000", List.of())), FeeBreakdown.empty());

        assertThat(lineOf(result, PricingRule.MERCHANT_DISCOUNT)).isEqualByComparingTo("-1000.00");
        assertThat(result.fee()).isEqualByComparingTo("0.00");
    }

    @Test
    void anAnonymousQuoteGetsNoDiscountLineAtAll() {
        FeeBreakdown result = new MerchantDiscountDecorator(BASE)
                .price(context(null), FeeBreakdown.empty());

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule).containsExactly(PricingRule.BASE);
    }

    @Test
    void waivedSurchargesSimplyDoNotAppear() {
        MerchantAgreement waiving = new MerchantAgreement(UUID.randomUUID(), MERCHANT, BigDecimal.ZERO,
                BigDecimal.ZERO, true, true, true, List.of());
        FeeBreakdown waived = new FeeCalculator().calculate(context(waiving));
        FeeBreakdown charged = new FeeCalculator().calculate(context(agreement("0", "0", List.of())));

        assertThat(waived.lines()).extracting(FeeBreakdown.Line::rule)
                .doesNotContain(PricingRule.REMOTE_AREA, PricingRule.INSURANCE, PricingRule.COD);
        assertThat(charged.lines()).extracting(FeeBreakdown.Line::rule)
                .contains(PricingRule.REMOTE_AREA, PricingRule.INSURANCE, PricingRule.COD);
    }

    @Test
    void theDiscountAppliesToTheDeliveryButNotToCoverOrCashHandling() {
        FeeBreakdown result = new FeeCalculator().calculate(context(agreement("0.50", "0", List.of())));

        // Cover and the cash commission come from the declared value and the cash collected,
        // so halving the delivery charge leaves both exactly where they were.
        assertThat(lineOf(result, PricingRule.INSURANCE)).isEqualByComparingTo("300.00");
        assertThat(lineOf(result, PricingRule.COD)).isEqualByComparingTo("30.00");
    }
}
