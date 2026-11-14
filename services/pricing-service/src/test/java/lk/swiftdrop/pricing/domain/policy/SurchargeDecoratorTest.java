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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The remote-area and insurance rules, each in isolation from the rest of the chain. */
class SurchargeDecoratorTest {

    private static final List<InsuranceBand> BANDS = List.of(
            new InsuranceBand(new BigDecimal("10000"), new BigDecimal("60"), new BigDecimal("0")),
            new InsuranceBand(new BigDecimal("50000"), new BigDecimal("100"), new BigDecimal("0.01")),
            new InsuranceBand(null, new BigDecimal("500"), new BigDecimal("0.02")));

    private static final RemoteAreaSurcharge JAFFNA =
            new RemoteAreaSurcharge("Jaffna", new BigDecimal("250.00"));
    private static final RemoteAreaSurcharge MANNAR =
            new RemoteAreaSurcharge("Mannar", new BigDecimal("400.00"));

    /** A strategy that charges a fixed base, so each decorator is measured on its own. */
    private static final PricingStrategy BASE = (context, soFar) ->
            soFar.plus(PricingRule.BASE, new BigDecimal("500.00"));

    private static PricingContext context(RemoteAreaSurcharge origin, RemoteAreaSurcharge destination,
                                          String declaredValue) {
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("NP"),
                new BigDecimal("475.00"), new BigDecimal("78.00"), new BigDecimal("1.500"),
                new BigDecimal("0.0150"), LocalDate.of(2026, 1, 1), null);
        SizeClassRate size = new SizeClassRate(SizeClassCode.M, new BigDecimal("5"),
                Dimensions.of("40", "30", "20"), new BigDecimal("1.000"));
        return new PricingContext(
                new PricingContext.Parcel(BigDecimal.ONE, BigDecimal.ONE, size, ServiceLevel.STANDARD,
                        BigDecimal.ZERO, declaredValue == null ? null : new BigDecimal(declaredValue)),
                new PricingContext.Rates(card, List.of(), origin, destination, BANDS));
    }

    private static BigDecimal lineOf(FeeBreakdown breakdown, PricingRule rule) {
        return breakdown.lines().stream().filter(line -> line.rule() == rule)
                .map(FeeBreakdown.Line::amount).findFirst().orElse(null);
    }

    @Test
    void chargesACollectionAndADeliveryThatAreBothHardToReach() {
        FeeBreakdown result = new RemoteAreaSurchargeDecorator(BASE)
                .price(context(JAFFNA, MANNAR, null), FeeBreakdown.empty());

        // Two awkward journeys, two charges, reported as one line.
        assertThat(lineOf(result, PricingRule.REMOTE_AREA)).isEqualByComparingTo("650.00");
    }

    @Test
    void chargesOnlyTheEndThatIsRemote() {
        assertThat(lineOf(new RemoteAreaSurchargeDecorator(BASE)
                .price(context(JAFFNA, null, null), FeeBreakdown.empty()), PricingRule.REMOTE_AREA))
                .isEqualByComparingTo("250.00");
        assertThat(lineOf(new RemoteAreaSurchargeDecorator(BASE)
                .price(context(null, MANNAR, null), FeeBreakdown.empty()), PricingRule.REMOTE_AREA))
                .isEqualByComparingTo("400.00");
    }

    @Test
    void addsNoLineWhenNeitherEndIsRemote() {
        FeeBreakdown result = new RemoteAreaSurchargeDecorator(BASE)
                .price(context(null, null, null), FeeBreakdown.empty());

        assertThat(result.lines()).extracting(FeeBreakdown.Line::rule).containsExactly(PricingRule.BASE);
    }

    @Test
    void theRemoteChargeDoesNotGrowWithTheDeliveryFee() {
        // The difficulty is the trip, not the parcel, so the same flat amount either way.
        PricingStrategy dearer = (context, soFar) -> soFar.plus(PricingRule.BASE, new BigDecimal("5000.00"));

        assertThat(lineOf(new RemoteAreaSurchargeDecorator(dearer)
                .price(context(JAFFNA, null, null), FeeBreakdown.empty()), PricingRule.REMOTE_AREA))
                .isEqualByComparingTo("250.00");
    }

    @Test
    void takesThePremiumFromTheBandTheDeclaredValueFallsIn() {
        // 8000 is in the first band, whose flat 60 beats its zero percentage.
        assertThat(lineOf(new InsuranceSurchargeDecorator(BASE)
                .price(context(null, null, "8000"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("60.00");
        // 30000 is in the second: one per cent is 300, which beats the flat 100.
        assertThat(lineOf(new InsuranceSurchargeDecorator(BASE)
                .price(context(null, null, "30000"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("300.00");
        // 1000000 falls in the open-ended band: two per cent.
        assertThat(lineOf(new InsuranceSurchargeDecorator(BASE)
                .price(context(null, null, "1000000"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("20000.00");
    }

    @Test
    void takesTheFlatFeeWhereThePercentageWouldNotCoverTheHandling() {
        // 12000 is in the second band: one per cent is 120, which beats the flat 100.
        assertThat(lineOf(new InsuranceSurchargeDecorator(BASE)
                .price(context(null, null, "12000"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("120.00");
        // 10001 is barely into it: one per cent is 100.01, only just above the flat fee.
        assertThat(lineOf(new InsuranceSurchargeDecorator(BASE)
                .price(context(null, null, "10001"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("100.01");
    }

    @Test
    void chargesNothingForAParcelSentWithoutCover() {
        for (String value : new String[] {null, "0"}) {
            FeeBreakdown result = new InsuranceSurchargeDecorator(BASE)
                    .price(context(null, null, value), FeeBreakdown.empty());

            assertThat(result.lines()).extracting(FeeBreakdown.Line::rule)
                    .containsExactly(PricingRule.BASE);
        }
    }

    @Test
    void thePremiumComesFromTheValueNotFromTheDeliveryFee() {
        PricingStrategy dearer = (context, soFar) -> soFar.plus(PricingRule.BASE, new BigDecimal("9000.00"));

        assertThat(lineOf(new InsuranceSurchargeDecorator(dearer)
                .price(context(null, null, "8000"), FeeBreakdown.empty()), PricingRule.INSURANCE))
                .isEqualByComparingTo("60.00");
    }
}
