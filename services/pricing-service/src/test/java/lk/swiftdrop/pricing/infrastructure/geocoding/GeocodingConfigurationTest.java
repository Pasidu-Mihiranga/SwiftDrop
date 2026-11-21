package lk.swiftdrop.pricing.infrastructure.geocoding;

import lk.swiftdrop.pricing.application.port.out.AddressGeocoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeocodingConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GeocodingConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues(
                    "swiftdrop.pricing.geocoding.user-agent=test-agent/1.0",
                    "swiftdrop.pricing.geocoding.country-codes=lk",
                    "swiftdrop.pricing.geocoding.connect-timeout=PT0.5S",
                    "swiftdrop.pricing.geocoding.read-timeout=PT3S",
                    "swiftdrop.pricing.geocoding.cache-ttl=PT24H",
                    "swiftdrop.pricing.geocoding.min-interval=PT1S",
                    "swiftdrop.pricing.geocoding.max-wait=PT2S",
                    "swiftdrop.pricing.geocoding.breaker-open-for=PT30S");

    @Test
    void registersNoGeocoderWithoutAUrl() {
        runner.withPropertyValues("swiftdrop.pricing.geocoding.url=").run(context ->
                assertThat(context).doesNotHaveBean(AddressGeocoder.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(AddressGeocoder.class));
    }

    @Test
    void registersNominatimWhenAUrlIsConfigured() {
        runner.withPropertyValues("swiftdrop.pricing.geocoding.url=http://nominatim:8080").run(context ->
                assertThat(context.getBean(AddressGeocoder.class)).isInstanceOf(NominatimGeocoder.class));
    }
}
