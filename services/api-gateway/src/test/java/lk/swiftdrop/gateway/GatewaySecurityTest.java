package lk.swiftdrop.gateway;

import lk.swiftdrop.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Downstream services are not running here (every route points at a closed port): we check what the
 * gateway lets through and how it answers when a service is down. Rate limiting is covered by
 * {@code RateLimitRedisIT}, so it is switched off here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "USER_SERVICE_URL=http://localhost:1",
        "MERCHANT_SERVICE_URL=http://localhost:1",
        "PRICING_SERVICE_URL=http://localhost:1",
        "PARCEL_SERVICE_URL=http://localhost:1",
        "DISPATCH_SERVICE_URL=http://localhost:1",
        "REPORTING_SERVICE_URL=http://localhost:1",
        "TRACKING_SERVICE_URL=http://localhost:1",
        "TRACKING_SERVICE_WS_URL=ws://localhost:1",
        "NOTIFICATION_SERVICE_URL=http://localhost:1",
        "WEBHOOK_SERVICE_URL=http://localhost:1",
        "AI_ASSISTANT_SERVICE_URL=http://localhost:1",
        "swiftdrop.gateway.rate-limit.enabled=false"
})
class GatewaySecurityTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private GatewaySecurityProperties security;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void protectedRoutesRequireAToken() {
        client.get().uri("/api/v1/parcels/123").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("WWW-Authenticate");
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    /** One test per entry in swiftdrop.gateway.public-paths, so a new public path is covered automatically. */
    @TestFactory
    Stream<DynamicTest> everyPublicPathIsReachableWithoutAToken() {
        assertThat(security.publicPaths()).isNotEmpty();
        return security.publicPaths().stream().map(pattern -> DynamicTest.dynamicTest(pattern, () -> {
            String path = pattern.replace("/**", "/sample");
            int status = client.get().uri(path).exchange().returnResult(String.class).getStatus().value();
            assertThat(status).as("GET %s without a token", path).isNotIn(401, 403);
        }));
    }

    @Test
    void aPrivateNeighbourOfAPublicPathStillNeedsAToken() {
        client.get().uri("/api/v1/tracking/SD123").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/admin/pricing/zones").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void aServiceThatIsDownAnswers503ProblemJsonWithoutNamingIt() {
        client.get().uri("/api/v1/pricing/zones").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("Retry-After", "5")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.detail").value(detail -> assertThat(detail.toString()).doesNotContain("pricing", "localhost"));
    }

    @Test
    void authenticatedRequestsAreForwardedWithACorrelationId() {
        var result = client.mutateWith(mockJwt()).get().uri("/api/v1/parcels/123").exchange()
                .returnResult(String.class);

        assertThat(result.getStatus().value()).isEqualTo(503);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}
