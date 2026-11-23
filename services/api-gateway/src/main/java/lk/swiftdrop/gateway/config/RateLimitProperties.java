package lk.swiftdrop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code swiftdrop.gateway.rate-limit}: requests per minute for a signed-in caller (a Keycloak user,
 * or a merchant API client by its {@code client_id}) and for an anonymous caller (by client IP). A
 * caller may burst up to a full minute's allowance, then the bucket refills evenly.
 * {@code trusted-proxies} are address prefixes (the web server, the ingress) whose
 * {@code X-Forwarded-For} is believed; from anyone else it is ignored.
 */
@ConfigurationProperties(prefix = "swiftdrop.gateway.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer authenticatedPerMinute, Integer anonymousPerMinute,
                                  List<String> trustedProxies) {

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        authenticatedPerMinute = positive(authenticatedPerMinute, 300);
        anonymousPerMinute = positive(anonymousPerMinute, 60);
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
