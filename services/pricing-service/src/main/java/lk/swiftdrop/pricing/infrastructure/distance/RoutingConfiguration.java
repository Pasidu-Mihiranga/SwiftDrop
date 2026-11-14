package lk.swiftdrop.pricing.infrastructure.distance;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Puts a router in front of the estimate when one is configured.
 *
 * <p>Nothing is registered without a URL, which leaves {@link HaversineRoadDistanceProvider} as
 * the only implementation of the port. The pricing code cannot tell the difference.
 *
 * <p>There is no retry. A customer is waiting on this call, the estimate is a usable answer,
 * and a retry would turn one slow router into twice the load on it.
 */
@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
@ConditionalOnExpression("!'${swiftdrop.pricing.routing.url:}'.isBlank()")
public class RoutingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RoutingConfiguration.class);

    @Bean
    @Primary
    RoadDistanceProvider routedRoadDistanceProvider(RoutingProperties routing, ObjectMapper mapper,
                                                    StringRedisTemplate redis,
                                                    HaversineRoadDistanceProvider estimate) {
        return new RoutedRoadDistanceProvider(
                new OsrmClient(restClient(routing), mapper),
                new RouteCache(redis, routing.cacheTtl()),
                breaker(routing),
                estimate);
    }

    static RestClient restClient(RoutingProperties routing) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(routing.connectTimeout());
        timeouts.setReadTimeout(routing.readTimeout());
        return RestClient.builder().baseUrl(routing.url()).requestFactory(timeouts).build();
    }

    /** Half of the last twenty calls failing opens it, once at least ten have been made. */
    static CircuitBreaker breaker(RoutingProperties routing) {
        CircuitBreaker breaker = CircuitBreaker.of("osrm", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(routing.breakerOpenFor())
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
        breaker.getEventPublisher().onStateTransition(event -> log.warn("Routing breaker: {}",
                event.getStateTransition()));
        return breaker;
    }
}
