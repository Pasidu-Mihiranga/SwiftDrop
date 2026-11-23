package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistanceBandScheduleTest {

    private static DistanceBand band(String min, String max, String fee) {
        return new DistanceBand(null, new BigDecimal(min), max == null ? null : new BigDecimal(max),
                new BigDecimal(fee), 2, 1);
    }

    @Test
    void acceptsACompleteSetAndReturnsItInDistanceOrder() {
        List<DistanceBand> ordered = DistanceBandSchedule.requireComplete(List.of(
                band("30", null, "150"), band("0", "30.00", "0")));

        assertThat(ordered).extracting(DistanceBand::minKm)
                .containsExactly(new BigDecimal("0"), new BigDecimal("30"));
    }

    @Test
    void refusesAnEmptySet() {
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesASetThatDoesNotStartAtZero() {
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of(band("5", null, "0"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("start at 0 km");
    }

    @Test
    void refusesAGapOrAnOverlapBetweenBands() {
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of(
                band("0", "30", "0"), band("40", null, "150"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The band ending at 30 km must be followed by one starting there, not at 40 km");
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of(
                band("0", "30", "0"), band("20", null, "150"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsOnlyTheLastBandToBeOpenEnded() {
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of(
                band("0", null, "0"), band("0.5", null, "150"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only the last band");
        assertThatThrownBy(() -> DistanceBandSchedule.requireComplete(List.of(band("0", "30", "0"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("open ended");
    }

    @Test
    void aSameDayCutoffRefusesFromTheCutoffItself() {
        PricingSettings settings = new PricingSettings(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalTime.of(14, 0));

        assertThat(settings.acceptsSameDayAt(LocalTime.of(13, 59))).isTrue();
        assertThat(settings.acceptsSameDayAt(LocalTime.of(14, 0))).isFalse();
        assertThat(new PricingSettings(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                .acceptsSameDayAt(LocalTime.of(23, 59))).isTrue();
    }
}
