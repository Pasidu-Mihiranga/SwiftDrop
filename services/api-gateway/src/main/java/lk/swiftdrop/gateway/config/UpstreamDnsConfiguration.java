package lk.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Keeps the gateway's view of a service's address fresh.
 *
 * <p>Routes resolve service names (pricing-service, parcel-service, ...) through reactor-netty's
 * own DNS resolver, which caches an answer for as long as its record says. Docker's DNS answers
 * with a long TTL, so after a container was recreated with a new IP the gateway kept connecting to
 * the old one and answered 503 until it was restarted. Capping the cache, and not caching failed
 * lookups, makes a recreated or rescheduled service reachable again within seconds. A lookup is
 * cheap next to the request it routes, so the short cache costs nothing noticeable.
 */
@Configuration(proxyBeanMethods = false)
public class UpstreamDnsConfiguration {

    @Bean
    public HttpClientCustomizer upstreamDnsCacheCustomizer(
            @Value("${swiftdrop.gateway.upstream-dns-cache-ttl:PT5S}") Duration maxTimeToLive) {
        return httpClient -> httpClient.resolver(spec -> spec
                .cacheMaxTimeToLive(maxTimeToLive)
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
