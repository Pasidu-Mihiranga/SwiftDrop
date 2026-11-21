package lk.swiftdrop.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The gateway gathers every service's OpenAPI document into one Swagger UI. Downstream services
 * are not running here, so only what the gateway itself serves and lets through is checked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "MERCHANT_SERVICE_URL=http://localhost:1",
        "PARCEL_SERVICE_URL=http://localhost:1"
})
class ApiDocsAggregationTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void theSwaggerUiOpensWithoutAToken() {
        int status = client.get().uri("/swagger-ui.html").exchange().returnResult(String.class).getStatus().value();

        // springdoc answers with the page or a redirect to it, never a login challenge.
        assertThat(status).isIn(200, 302);
    }

    @Test
    void theSwaggerUiOffersADocumentForEachService() {
        client.get().uri("/api-docs/gateway/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls[?(@.name == 'merchant-service')].url").isEqualTo("/api-docs/merchant-service")
                .jsonPath("$.urls[?(@.name == 'parcel-service')].url").isEqualTo("/api-docs/parcel-service")
                .jsonPath("$.urls[?(@.name == 'pricing-service')].url").isEqualTo("/api-docs/pricing-service")
                .jsonPath("$['urls.primaryName']").isEqualTo("merchant-service");
    }

    @Test
    void aServiceDocumentIsForwardedWithoutAToken() {
        int status = client.get().uri("/api-docs/merchant-service").exchange()
                .returnResult(String.class).getStatus().value();

        // Forwarded to the (absent) merchant-service, so the gateway answers 5xx — but never 401/403.
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void theApiItselfStillNeedsAToken() {
        client.get().uri("/api/v1/merchant/orders").exchange().expectStatus().isUnauthorized();
    }
}
