package lk.swiftdrop.gateway.filter;

import lk.swiftdrop.gateway.config.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caller rate limit (token bucket). Signed-in callers are counted by their Keycloak user id,
 * everyone else by client IP. Over the limit the gateway answers {@code 429 RATE_LIMITED} with
 * {@code Retry-After}, and the request never reaches a service.
 *
 * <p>Buckets live in this gateway's memory, so with several replicas each one allows the full
 * limit; that is enough to stop a single client flooding the system.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final long IDLE_MILLIS = 10 * 60_000L;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitGlobalFilter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RateLimitGlobalFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled() || exchange.getRequest().getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> context.getAuthentication() instanceof JwtAuthenticationToken jwt ? "user:" + jwt.getName() : null)
                .map(key -> new Caller(key, properties.authenticatedPerMinute()))
                .switchIfEmpty(Mono.fromSupplier(() -> new Caller("ip:" + clientIp(exchange), properties.anonymousPerMinute())))
                .flatMap(caller -> {
                    long remaining = take(caller);
                    if (remaining < 0) return tooManyRequests(exchange.getResponse(), caller);
                    exchange.getResponse().getHeaders().set(REMAINING_HEADER, Long.toString(remaining));
                    return chain.filter(exchange);
                });
    }

    /** Takes one token; returns the tokens left, or -1 when the bucket is empty. */
    long take(Caller caller) {
        long now = clock.millis();
        if (buckets.size() >= properties.maxTrackedKeys()) {
            buckets.values().removeIf(bucket -> bucket.idleSince(now, IDLE_MILLIS));
        }
        return buckets.computeIfAbsent(caller.key(), key -> new Bucket(caller.perMinute(), now)).take(caller.perMinute(), now);
    }

    private Mono<Void> tooManyRequests(ServerHttpResponse response, Caller caller) {
        long retryAfter = Math.max(1, (long) Math.ceil(60.0 / caller.perMinute()));
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("Retry-After", Long.toString(retryAfter));
        response.getHeaders().set(REMAINING_HEADER, "0");
        byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"code\":\"RATE_LIMITED\",\"detail\":\"Too many requests. Try again in " + retryAfter + " seconds.\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // right after the correlation id, before routing
    }

    record Caller(String key, int perMinute) {
    }

    /** Token bucket holding at most one minute's allowance, refilled continuously. */
    static final class Bucket {
        private double tokens;
        private long updatedAt;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.updatedAt = now;
        }

        synchronized long take(int perMinute, long now) {
            tokens = Math.min(perMinute, tokens + (now - updatedAt) * perMinute / 60_000.0);
            updatedAt = now;
            if (tokens < 1) return -1;
            tokens -= 1;
            return (long) tokens;
        }

        synchronized boolean idleSince(long now, long idleMillis) {
            return now - updatedAt > idleMillis;
        }
    }
}
