package lk.swiftdrop.pricing.infrastructure.geocoding;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Registers address search only when a geocoder URL is configured. Without one there is no
 * {@link AddressGeocoder} bean and the search endpoint answers 503, so no address ever leaves the
 * service unless an operator chose a geocoder.
 */
@Configuration
@EnableConfigurationProperties(GeocodingProperties.class)
@ConditionalOnExpression("!'${swiftdrop.pricing.geocoding.url:}'.isBlank()")
public class GeocodingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GeocodingConfiguration.class);

    @Bean
    AddressGeocoder nominatimGeocoder(GeocodingProperties geocoding, ObjectMapper mapper, StringRedisTemplate redis,
                                      Clock clock) {
        return new NominatimGeocoder(restClient(geocoding), mapper, geocoding,
                new GeocodeCache(redis, mapper, geocoding.cacheTtl()), breaker(geocoding),
                new MinimumInterval(geocoding.minInterval(), geocoding.maxWait(), clock));
    }

    static RestClient restClient(GeocodingProperties geocoding) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(geocoding.connectTimeout());
        timeouts.setReadTimeout(geocoding.readTimeout());
        return RestClient.builder().baseUrl(geocoding.url()).requestFactory(timeouts)
                .defaultHeader(HttpHeaders.USER_AGENT, geocoding.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .build();
    }

    /** Half of the last ten calls failing opens it, once at least five have been made. */
    static CircuitBreaker breaker(GeocodingProperties geocoding) {
        CircuitBreaker breaker = CircuitBreaker.of("geocoder", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(geocoding.breakerOpenFor())
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Geocoder breaker: {}",
                event.getStateTransition()));
        return breaker;
    }
}
