package lk.swiftdrop.pricing.domain.model;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SizeClassRateTest {

    private static SizeClassRate medium(BigDecimal multiplier) {
        return new SizeClassRate(SizeClassCode.M, new BigDecimal("5.00"), Dimensions.of("40", "30", "20"), multiplier);
    }

    @Test
    void acceptsAParcelUpToItsWeightLimitInclusive() {
        SizeClassRate rate = medium(new BigDecimal("1.150"));

        assertThat(rate.accepts(new BigDecimal("4.99"))).isTrue();
        assertThat(rate.accepts(new BigDecimal("5.00"))).isTrue();
        assertThat(rate.accepts(new BigDecimal("5.01"))).isFalse();
        assertThat(rate.accepts((BigDecimal) null)).isFalse();
    }

    @Test
    void rejectsAMultiplierBelowOneBecauseASizeCannotDiscount() {
        assertThatThrownBy(() -> medium(new BigDecimal("0.900")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> medium(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresACodeAndAPositiveWeightLimit() {
        assertThatThrownBy(() -> new SizeClassRate(null, BigDecimal.ONE, Dimensions.of("40", "30", "20"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SizeClassRate(SizeClassCode.S, BigDecimal.ZERO, Dimensions.of("40", "30", "20"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
