package lk.swiftdrop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code swiftdrop.gateway.rate-limit}: requests per minute for a signed-in user (keyed by the
 * Keycloak user id) and for an anonymous caller (keyed by client IP). Each key may burst up to its
 * full minute's allowance, then refills evenly. {@code trusted-proxies} are address prefixes (the web
 * server, the ingress) whose {@code X-Forwarded-For} is believed; from anyone else it is ignored.
 */
@ConfigurationProperties(prefix = "swiftdrop.gateway.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer authenticatedPerMinute, Integer anonymousPerMinute, Integer maxTrackedKeys,
                                  List<String> trustedProxies) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        enabled = enabled == null || enabled;
        authenticatedPerMinute = positive(authenticatedPerMinute, 300);
        anonymousPerMinute = positive(anonymousPerMinute, 60);
        maxTrackedKeys = positive(maxTrackedKeys, 100_000);
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
