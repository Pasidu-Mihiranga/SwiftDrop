package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateCardTest {

    private static final ZoneCode WESTERN = ZoneCode.of("WP");
    private static final ZoneCode CENTRAL = ZoneCode.of("CP");
    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

    private static RateCard card(LocalDate from, LocalDate to) {
        return new RateCard(UUID.randomUUID(), WESTERN, CENTRAL, new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"), from, to);
    }

    @Test
    void anOpenEndedCardIsInForceFromItsStartDateOnwards() {
        RateCard open = card(JANUARY, null);

        assertThat(open.isInForceOn(JANUARY.minusDays(1))).isFalse();
        assertThat(open.isInForceOn(JANUARY)).isTrue();
        assertThat(open.isInForceOn(JANUARY.plusYears(5))).isTrue();
    }

    @Test
    void theEndDateIsExclusiveSoACardStopsApplyingOnTheDayItEnds() {
        RateCard closed = card(JANUARY, JULY);

        assertThat(closed.isInForceOn(JULY.minusDays(1))).isTrue();
        assertThat(closed.isInForceOn(JULY)).isFalse();
    }

    @Test
    void closingKeepsEveryFeeAndIdentitySoIssuedQuotesStayAuditable() {
        RateCard open = card(JANUARY, null);

        RateCard closed = open.closedOn(JULY);

        assertThat(closed.id()).isEqualTo(open.id());
        assertThat(closed.baseFee()).isEqualTo(open.baseFee());
        assertThat(closed.effectiveFrom()).isEqualTo(JANUARY);
        assertThat(closed.effectiveTo()).isEqualTo(JULY);
    }

    @Test
    void rejectsAnEndDateThatIsNotAfterTheStartDate() {
        assertThatThrownBy(() -> card(JANUARY, JANUARY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card(JULY, JANUARY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresBothZonesAndAStartDate() {
        assertThatThrownBy(() -> new RateCard(null, null, CENTRAL, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateCard(null, WESTERN, CENTRAL, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFeesAnExpressDiscountAndACodFractionAboveOne() {
        assertThatThrownBy(() -> new RateCard(null, WESTERN, CENTRAL, new BigDecimal("-1"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base fee");
        assertThatThrownBy(() -> new RateCard(null, WESTERN, CENTRAL, BigDecimal.ONE, new BigDecimal("-1"),
                BigDecimal.ONE, BigDecimal.ZERO, JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-kilogram");
        assertThatThrownBy(() -> new RateCard(null, WESTERN, CENTRAL, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("0.5"), BigDecimal.ZERO, JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("express");
        assertThatThrownBy(() -> new RateCard(null, WESTERN, CENTRAL, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("1.5"), JANUARY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fraction");
    }
}
