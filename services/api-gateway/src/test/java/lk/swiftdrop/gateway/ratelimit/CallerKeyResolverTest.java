package lk.swiftdrop.gateway.ratelimit;

import lk.swiftdrop.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallerKeyResolverTest {

    private static CallerKeyResolver resolver(boolean enabled, List<String> trusted) {
        return new CallerKeyResolver(new RateLimitProperties(enabled, 300, 60, trusted));
    }

    private static MockServerWebExchange request(String ip, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress(ip, 5000));
        if (forwardedFor != null) builder.header("X-Forwarded-For", forwardedFor);
        return MockServerWebExchange.from(builder);
    }

    private static String keyFor(CallerKeyResolver resolver, Jwt jwt) {
        return resolver.resolve(request("203.0.113.5", null))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)))
                .block();
    }

    @Test
    void signedInPersonIsKeyedByKeycloakId() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-1").build();
        assertThat(keyFor(resolver(true, List.of()), jwt)).isEqualTo("user:user-1");
    }

    @Test
    void merchantApiClientIsKeyedByClientId() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("service-account-id").claim("client_id", "merchant-abc").build();
        assertThat(keyFor(resolver(true, List.of()), jwt)).isEqualTo("client:merchant-abc");
    }

    @Test
    void anonymousCallerIsKeyedByIp() {
        assertThat(resolver(true, List.of()).resolve(request("203.0.113.5", null)).block()).isEqualTo("ip:203.0.113.5");
    }

    @Test
    void forwardedForIsBelievedOnlyFromTrustedProxies() {
        CallerKeyResolver resolver = resolver(true, List.of("127.0.0.1"));
        assertThat(resolver.clientIp(request("127.0.0.1", "198.51.100.1, 198.51.100.9"))).isEqualTo("198.51.100.9");
        assertThat(resolver.clientIp(request("127.0.0.1", null))).isEqualTo("127.0.0.1");
        assertThat(resolver.clientIp(request("127.0.0.1", " , "))).isEqualTo("127.0.0.1");
        assertThat(resolver.clientIp(request("203.0.113.5", "198.51.100.9"))).isEqualTo("203.0.113.5");
    }

    @Test
    void switchedOffGivesNoKeySoNothingIsLimited() {
        assertThat(resolver(false, List.of()).resolve(request("203.0.113.5", null)).blockOptional()).isEmpty();
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        RateLimitProperties defaults = new RateLimitProperties(null, null, 0, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.authenticatedPerMinute()).isEqualTo(300);
        assertThat(defaults.anonymousPerMinute()).isEqualTo(60);
        assertThat(defaults.trustedProxies()).isEmpty();
    }
}
