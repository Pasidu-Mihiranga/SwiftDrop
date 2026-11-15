package lk.swiftdrop.pricing.infrastructure.distance;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutedRoadDistanceProviderTest {

    private static final GeoPoint EXACT_A = GeoPoint.of("6.927079", "79.861244");
    private static final GeoPoint EXACT_B = GeoPoint.of("7.290572", "80.633728");
    private static final GeoPoint ROUNDED_A = GeoPoint.of("6.927", "79.861");
    private static final GeoPoint ROUNDED_B = GeoPoint.of("7.291", "80.634");
    private static final LaneDistance ESTIMATE =
            new LaneDistance(new BigDecimal("122.20"), DistanceSource.ESTIMATED);

    private OsrmClient osrm;
    private RouteCache cache;
    private RoadDistanceProvider estimate;
    private CircuitBreaker breaker;
    private RoutedRoadDistanceProvider provider;

    @BeforeEach
    void setUp() {
        osrm = mock(OsrmClient.class);
        cache = mock(RouteCache.class);
        estimate = mock(RoadDistanceProvider.class);
        when(cache.find(any(), any())).thenReturn(Optional.empty());
        when(estimate.between(EXACT_A, EXACT_B)).thenReturn(ESTIMATE);
        breaker = RoutingConfiguration.breaker(new RoutingProperties("http://osrm:5000",
                Duration.ofMillis(300), Duration.ofSeconds(1), Duration.ofHours(24), Duration.ofMinutes(1)));
        provider = new RoutedRoadDistanceProvider(osrm, cache, breaker, estimate);
    }

    @Test
    void measuresTheRoundedPointsAndRemembersTheResult() {
        when(osrm.drivingKm(ROUNDED_A, ROUNDED_B)).thenReturn(Optional.of(new BigDecimal("115.20")));

        LaneDistance distance = provider.between(EXACT_A, EXACT_B);

        assertThat(distance).isEqualTo(new LaneDistance(new BigDecimal("115.20"), DistanceSource.ROUTED));
        verify(cache).put(ROUNDED_A, ROUNDED_B, new BigDecimal("115.20"));
    }

    @Test
    void aCachedRouteIsRoutedAndNeverReachesTheRouter() {
        when(cache.find(ROUNDED_A, ROUNDED_B)).thenReturn(Optional.of(new BigDecimal("115.20")));

        assertThat(provider.between(EXACT_A, EXACT_B).source()).isEqualTo(DistanceSource.ROUTED);
        verify(osrm, never()).drivingKm(any(), any());
    }

    @Test
    void estimatesFromTheExactPointsWhenTheRouterIsDownAndDoesNotCacheIt() {
        when(osrm.drivingKm(any(), any())).thenThrow(new RoutingUnavailableException("down"));

        assertThat(provider.between(EXACT_A, EXACT_B)).isEqualTo(ESTIMATE);
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void estimatesWhenTheMapHasNoRouteAndDoesNotCacheIt() {
        when(osrm.drivingKm(any(), any())).thenReturn(Optional.empty());

        assertThat(provider.between(EXACT_A, EXACT_B)).isEqualTo(ESTIMATE);
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void noRouteAnswersDoNotOpenTheBreaker() {
        when(osrm.drivingKm(any(), any())).thenReturn(Optional.empty());

        for (int i = 0; i < 25; i++) {
            provider.between(EXACT_A, EXACT_B);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(osrm, times(25)).drivingKm(any(), any());
    }

    @Test
    void stopsCallingARouterThatKeepsFailingButKeepsQuoting() {
        when(osrm.drivingKm(any(), any())).thenThrow(new RoutingUnavailableException("down"));

        for (int i = 0; i < 25; i++) {
            assertThat(provider.between(EXACT_A, EXACT_B)).isEqualTo(ESTIMATE);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(osrm, times(10)).drivingKm(any(), any());
    }
}
