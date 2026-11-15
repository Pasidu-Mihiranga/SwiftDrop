package lk.swiftdrop.pricing.infrastructure.distance;

import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteCacheTest {

    private static final GeoPoint A = GeoPoint.of("6.927", "79.861");
    private static final GeoPoint B = GeoPoint.of("7.291", "80.634");
    private static final Duration DAY = Duration.ofHours(24);

    private ValueOperations<String, String> values;
    private RouteCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new RouteCache(redis, DAY);
    }

    @Test
    void readsBackADistanceWithItsScaleIntact() {
        when(values.get("pricing:route:6.927,79.861:7.291,80.634")).thenReturn("115.20");

        assertThat(cache.find(A, B)).hasValueSatisfying(km -> assertThat(km).isEqualTo(new BigDecimal("115.20")));
    }

    @Test
    void storesWithTheConfiguredLifetime() {
        cache.put(A, B, new BigDecimal("115.20"));

        verify(values).set("pricing:route:6.927,79.861:7.291,80.634", "115.20", DAY);
    }

    @Test
    void keepsTheTwoDirectionsApart() {
        cache.find(B, A);

        verify(values).get("pricing:route:7.291,80.634:6.927,79.861");
    }

    @Test
    void anUnreachableCacheIsAMissRatherThanAFailedQuote() {
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("redis is down"));

        assertThat(cache.find(A, B)).isEmpty();
    }

    @Test
    void failingToStoreDoesNotFailTheQuote() {
        doThrow(new QueryTimeoutException("redis is down")).when(values)
                .set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.put(A, B, BigDecimal.TEN)).doesNotThrowAnyException();
    }
}
