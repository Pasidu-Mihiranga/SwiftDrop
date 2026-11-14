package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SizeClassSelectorTest {

    private static final SizeClassRate S =
            rate(SizeClassCode.S, "2.00", "30", "20", "15", "1.000");
    private static final SizeClassRate M =
            rate(SizeClassCode.M, "5.00", "40", "30", "20", "1.150");
    private static final SizeClassRate L =
            rate(SizeClassCode.L, "15.00", "60", "40", "30", "1.350");
    private static final List<SizeClassRate> ALL = List.of(S, M, L);

    private static SizeClassRate rate(SizeClassCode code, String maxWeight,
                                      String length, String width, String height, String multiplier) {
        return new SizeClassRate(code, new BigDecimal(maxWeight),
                Dimensions.of(length, width, height), new BigDecimal(multiplier));
    }

    @Test
    void picksTheCheapestClassTheParcelGenuinelyFits() {
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("1"), Dimensions.of("25", "15", "10")))
                .contains(S);
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("1"), Dimensions.of("35", "25", "18")))
                .contains(M);
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("1"), Dimensions.of("55", "38", "28")))
                .contains(L);
    }

    @Test
    void weightCanPushAParcelIntoALargerClassThanItsShapeNeeds() {
        // Small enough for S by geometry, too heavy for it.
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("4"), Dimensions.of("25", "15", "10")))
                .contains(M);
    }

    @Test
    void aParcelThatFitsNoClassSelectsNothingRatherThanTheLargest() {
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("1"), Dimensions.of("200", "10", "10")))
                .isEmpty();
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("99"), Dimensions.of("25", "15", "10")))
                .isEmpty();
    }

    @Test
    void withoutMeasurementsThereIsNothingToSelectOn() {
        assertThat(SizeClassSelector.select(ALL, new BigDecimal("1"), null)).isEmpty();
        assertThat(SizeClassSelector.select(null, new BigDecimal("1"), Dimensions.of("1", "1", "1")))
                .isEmpty();
    }
}
