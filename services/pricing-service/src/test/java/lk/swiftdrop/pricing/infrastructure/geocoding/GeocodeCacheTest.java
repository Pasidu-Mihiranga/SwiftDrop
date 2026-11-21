package lk.swiftdrop.pricing.infrastructure.geocoding;

import lk.swiftdrop.pricing.application.port.out.AddressGeocoder.Match;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeocodeCacheTest {

    private ValueOperations<String, String> values;
    private GeocodeCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new GeocodeCache(redis, JsonMapper.builder().build(), Duration.ofHours(1));
    }

    @Test
    void storesUnderAHashSoTheAddressIsNotTheKey() {
        String key = GeocodeCache.key(" 45 Galle Road ", 5);

        assertThat(key).startsWith("pricing:geocode:").doesNotContain("Galle").hasSize("pricing:geocode:".length() + 64);
        assertThat(GeocodeCache.key("45 galle road", 5)).isEqualTo(key);
        assertThat(GeocodeCache.key("45 galle road", 6)).isNotEqualTo(key);
    }

    @Test
    void roundTripsMatches() {
        List<Match> matches = List.of(new Match("Kandy", GeoPoint.of("7.29", "80.63")));
        cache.put("Kandy", 5, matches);
        org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(GeocodeCache.key("Kandy", 5)), json.capture(), eq(Duration.ofHours(1)));

        when(values.get(GeocodeCache.key("Kandy", 5))).thenReturn(json.getValue());

        assertThat(cache.find("Kandy", 5)).contains(matches);
        assertThat(cache.find("Galle", 5)).isEmpty();
    }

    @Test
    void treatsAnUnreachableOrCorruptCacheAsAMiss() {
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("down"));
        assertThat(cache.find("Kandy", 5)).isEmpty();

        org.mockito.Mockito.doReturn("not json").when(values).get(anyString());
        assertThat(cache.find("Kandy", 5)).isEmpty();

        doThrow(new QueryTimeoutException("down")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> cache.put("Kandy", 5, List.of())).doesNotThrowAnyException();
    }
}
