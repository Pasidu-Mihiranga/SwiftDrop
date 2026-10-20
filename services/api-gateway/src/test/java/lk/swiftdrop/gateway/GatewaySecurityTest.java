package lk.swiftdrop.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/** Downstream services are not running here: we only check what the gateway lets through. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "PARCEL_SERVICE_URL=http://localhost:1",
        "PRICING_SERVICE_URL=http://localhost:1"
})
class GatewaySecurityTest {

    @Autowired
    private ApplicationContext context;

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

    @Test
    void publicRoutesSkipAuthenticationAndAreForwarded() {
        int status = client.get().uri("/api/v1/pricing/zones").exchange()
                .returnResult(String.class).getStatus().value();

        // Forwarded to the (absent) pricing-service, so the gateway answers 5xx — but never 401/403.
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void authenticatedRequestsAreForwardedWithACorrelationId() {
        var result = client.mutateWith(mockJwt()).get().uri("/api/v1/parcels/123").exchange()
                .returnResult(String.class);

        assertThat(result.getStatus().value()).isNotIn(401, 403);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}
