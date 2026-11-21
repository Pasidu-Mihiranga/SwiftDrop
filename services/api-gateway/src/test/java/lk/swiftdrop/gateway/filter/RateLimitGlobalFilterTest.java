package lk.swiftdrop.gateway.filter;

import lk.swiftdrop.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitGlobalFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };

    private RateLimitGlobalFilter filter(int user, int anon, List<String> trusted) {
        return new RateLimitGlobalFilter(new RateLimitProperties(true, user, anon, 1000, trusted), clock);
    }

    private static MockServerWebExchange request(String path, String ip, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path).remoteAddress(new InetSocketAddress(ip, 5000));
        if (forwardedFor != null) builder.header("X-Forwarded-For", forwardedFor);
        return MockServerWebExchange.from(builder);
    }

    private HttpStatus run(RateLimitGlobalFilter filter, MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void anonymousCallerIsLimitedPerIpAndGets429WithRetryAfter() {
        RateLimitGlobalFilter filter = filter(10, 2, List.of());
        assertThat(run(filter, request("/api/v1/pricing/zones", "203.0.113.5", null))).isNull();
        assertThat(run(filter, request("/api/v1/pricing/zones", "203.0.113.5", null))).isNull();

        MockServerWebExchange third = request("/api/v1/pricing/zones", "203.0.113.5", null);
        assertThat(run(filter, third)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("30");
        assertThat(third.getResponse().getBodyAsString().block()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(forwarded).hasValue(2);

        // another IP has its own bucket
        assertThat(run(filter, request("/api/v1/pricing/zones", "203.0.113.6", null))).isNull();
    }

    @Test
    void bucketRefillsOverTime() {
        RateLimitGlobalFilter filter = filter(10, 1, List.of());
        run(filter, request("/x", "203.0.113.5", null));
        assertThat(run(filter, request("/x", "203.0.113.5", null))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        now.addAndGet(60_000);
        assertThat(run(filter, request("/x", "203.0.113.5", null))).isNull();
    }

    @Test
    void signedInUserIsCountedByUserIdWithTheHigherLimit() {
        RateLimitGlobalFilter filter = filter(3, 1, List.of());
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-1").build();
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = request("/api/v1/users/me", "203.0.113.5", null);
            filter.filter(exchange, chain).contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))).block();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(exchange.getResponse().getHeaders().getFirst(RateLimitGlobalFilter.REMAINING_HEADER)).isEqualTo(String.valueOf(2 - i));
        }
        MockServerWebExchange fourth = request("/api/v1/users/me", "203.0.113.5", null);
        filter.filter(fourth, chain).contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))).block();
        assertThat(fourth.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void forwardedForIsBelievedOnlyFromTrustedProxies() {
        RateLimitGlobalFilter filter = filter(10, 1, List.of("127.0.0.1"));
        assertThat(filter.clientIp(request("/x", "127.0.0.1", "198.51.100.1, 198.51.100.9"))).isEqualTo("198.51.100.9");
        assertThat(filter.clientIp(request("/x", "127.0.0.1", null))).isEqualTo("127.0.0.1");
        assertThat(filter.clientIp(request("/x", "203.0.113.5", "198.51.100.9"))).isEqualTo("203.0.113.5");

        // two visitors behind the web server get separate buckets
        assertThat(run(filter, request("/x", "127.0.0.1", "198.51.100.1"))).isNull();
        assertThat(run(filter, request("/x", "127.0.0.1", "198.51.100.2"))).isNull();
    }

    @Test
    void actuatorAndDisabledLimiterAreNeverLimited() {
        RateLimitGlobalFilter filter = filter(1, 1, List.of());
        for (int i = 0; i < 3; i++) assertThat(run(filter, request("/actuator/health", "203.0.113.5", null))).isNull();

        RateLimitGlobalFilter off = new RateLimitGlobalFilter(new RateLimitProperties(false, 1, 1, 1000, null), clock);
        for (int i = 0; i < 3; i++) assertThat(run(off, request("/x", "203.0.113.5", null))).isNull();
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        RateLimitProperties defaults = new RateLimitProperties(null, null, 0, null, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.authenticatedPerMinute()).isEqualTo(300);
        assertThat(defaults.anonymousPerMinute()).isEqualTo(60);
        assertThat(defaults.trustedProxies()).isEmpty();
    }
}
