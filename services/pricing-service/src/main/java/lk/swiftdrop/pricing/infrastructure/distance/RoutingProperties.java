package lk.swiftdrop.pricing.infrastructure.distance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How to reach the routing engine.
 *
 * @param url            base URL of OSRM; blank means there is no router
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout    how long to wait for an answer
 * @param cacheTtl       how long a measured route is remembered
 * @param breakerOpenFor how long to stop calling a router that keeps failing
 */
@ConfigurationProperties("swiftdrop.pricing.routing")
public record RoutingProperties(String url, Duration connectTimeout, Duration readTimeout,
                                Duration cacheTtl, Duration breakerOpenFor) {
}
