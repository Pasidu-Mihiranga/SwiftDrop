package lk.swiftdrop.gateway.ratelimit;

import lk.swiftdrop.gateway.config.RateLimitProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Who a request is counted against: {@code client:<client_id>} for a merchant API client
 * (client-credentials token), {@code user:<keycloak id>} for a signed-in person, and
 * {@code ip:<address>} for everyone else. An empty key (limiter switched off) lets the request through.
 */
public class CallerKeyResolver implements KeyResolver {

    public static final String ANONYMOUS_PREFIX = "ip:";

    private final RateLimitProperties properties;

    public CallerKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        if (!properties.enabled()) return Mono.empty();
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> context.getAuthentication() instanceof JwtAuthenticationToken token ? keyOf(token.getToken()) : null)
                .switchIfEmpty(Mono.fromSupplier(() -> ANONYMOUS_PREFIX + clientIp(exchange)));
    }

    private static String keyOf(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        return clientId != null && !clientId.isBlank() ? "client:" + clientId : "user:" + jwt.getSubject();
    }

    /**
     * The connecting address, or the last X-Forwarded-For hop when the connection comes from a trusted
     * proxy (the web server or the ingress). Anyone else could set that header to dodge the limit.
     */
    String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String direct = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        if (properties.trustedProxies().stream().noneMatch(direct::startsWith)) return direct;
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return direct;
        String[] hops = forwarded.split(",");
        String last = hops[hops.length - 1].trim();
        return last.isEmpty() ? direct : last;
    }
}
