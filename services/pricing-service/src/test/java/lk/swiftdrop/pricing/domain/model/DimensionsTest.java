package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DimensionsTest {

    @Test
    void volumeIsTheProductOfTheThreeSides() {
        assertThat(Dimensions.of("40", "30", "20").volumeCm3()).isEqualByComparingTo("24000");
        assertThat(Dimensions.of("10.5", "10", "10").volumeCm3()).isEqualByComparingTo("1050");
    }

    @Test
    void aParcelFitsAClassWhateverWayRoundItIsMeasured() {
        Dimensions limit = Dimensions.of("40", "30", "20");

        assertThat(Dimensions.of("40", "30", "20").fitsWithin(limit)).isTrue();
        // The same box, measured in a different order, is still the same box.
        assertThat(Dimensions.of("20", "40", "30").fitsWithin(limit)).isTrue();
        assertThat(Dimensions.of("30", "20", "40").fitsWithin(limit)).isTrue();
        assertThat(Dimensions.of("39.9", "29.9", "19.9").fitsWithin(limit)).isTrue();
    }

    @Test
    void aParcelLongerThanTheClassInAnyDirectionDoesNotFit() {
        Dimensions limit = Dimensions.of("40", "30", "20");

        assertThat(Dimensions.of("41", "30", "20").fitsWithin(limit)).isFalse();
        assertThat(Dimensions.of("40", "30", "21").fitsWithin(limit)).isFalse();
        // Equal volume, but it is a plank and the box is not plank shaped.
        assertThat(Dimensions.of("120", "10", "20").fitsWithin(limit)).isFalse();
        assertThat(Dimensions.of("10", "10", "10").fitsWithin(null)).isFalse();
    }

    @Test
    void labelsItselfTheWayTheSizeClassSchemaPublishesIt() {
        assertThat(Dimensions.of("40", "30", "20").label()).isEqualTo("40x30x20");
        assertThat(Dimensions.of("40.0", "30.00", "20").label()).isEqualTo("40x30x20");
        assertThat(Dimensions.of("40.5", "30", "20").label()).isEqualTo("40.5x30x20");
    }

    @Test
    void refusesASideThatIsNotAPositiveLength() {
        assertThatThrownBy(() -> new Dimensions(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("length");
        assertThatThrownBy(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("width");
        assertThatThrownBy(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("height");
    }
}
