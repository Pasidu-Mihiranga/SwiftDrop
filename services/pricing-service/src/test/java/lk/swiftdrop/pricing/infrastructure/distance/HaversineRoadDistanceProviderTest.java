package lk.swiftdrop.pricing.infrastructure.distance;

import lk.swiftdrop.pricing.application.port.out.DistanceRateRepository;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HaversineRoadDistanceProviderTest {

    private static final GeoPoint COLOMBO = GeoPoint.of("6.927079", "79.861244");
    private static final GeoPoint KANDY = GeoPoint.of("7.290572", "80.633728");

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T09:00:00Z"), ZoneId.of("Asia/Colombo"));

    private static HaversineRoadDistanceProvider withRoadFactor(String factor) {
        DistanceRateRepository rates = mock(DistanceRateRepository.class);
        when(rates.findInForce(any())).thenReturn(factor == null ? Optional.empty()
                : Optional.of(new DistanceRate(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
                        new BigDecimal(factor))));
        return new HaversineRoadDistanceProvider(rates, CLOCK);
    }

    @Test
    void measuresTheGreatCircleBetweenTwoRealPlaces() {
        // Colombo to Kandy is about 94 km in a straight line.
        assertThat(HaversineRoadDistanceProvider.straightLineKm(COLOMBO, KANDY).doubleValue())
                .isCloseTo(94.0, within(2.0));
    }

    @Test
    void twoPointsInTheSamePlaceAreNoDistanceApart() {
        assertThat(HaversineRoadDistanceProvider.straightLineKm(COLOMBO, COLOMBO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void scalesTheStraightLineByTheConfiguredRoadFactor() {
        BigDecimal straight = HaversineRoadDistanceProvider.straightLineKm(COLOMBO, KANDY);

        assertThat(withRoadFactor("1.300").between(COLOMBO, KANDY).kilometres().doubleValue())
                .isCloseTo(straight.doubleValue() * 1.3, within(0.05));
    }

    @Test
    void saysPlainlyThatTheAnswerIsAnEstimate() {
        assertThat(withRoadFactor("1.300").between(COLOMBO, KANDY).source())
                .isEqualTo(DistanceSource.ESTIMATED);
    }

    @Test
    void fallsBackToTheStraightLineWhenNoRateIsConfigured() {
        BigDecimal straight = HaversineRoadDistanceProvider.straightLineKm(COLOMBO, KANDY);

        assertThat(withRoadFactor(null).between(COLOMBO, KANDY).kilometres())
                .isEqualByComparingTo(straight);
    }
}
