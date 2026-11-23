package lk.swiftdrop.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/** RequestRateLimiter against a real Redis: anonymous callers by IP, signed-in users by id, separate buckets. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "PRICING_SERVICE_URL=http://localhost:1",
        "USER_SERVICE_URL=http://localhost:1",
        "swiftdrop.gateway.rate-limit.anonymous-per-minute=2",
        "swiftdrop.gateway.rate-limit.authenticated-per-minute=3"
})
class RateLimitRedisIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void anonymousCallerGets429AfterTheLimitWithRetryAfter() {
        // MOCK requests have no remote address, so every anonymous request here shares one bucket.
        for (int i = 0; i < 2; i++) {
            client.get().uri("/api/v1/pricing/zones").exchange().expectStatus().isEqualTo(503); // forwarded, service down
        }
        client.get().uri("/api/v1/pricing/zones").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");
    }

    @Test
    void signedInUsersHaveTheirOwnBucketsAndTheHigherLimit() {
        for (int i = 0; i < 3; i++) {
            int status = client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-a"))).get().uri("/api/v1/users/me")
                    .exchange().returnResult(String.class).getStatus().value();
            assertThat(status).isEqualTo(503);
        }
        client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-a"))).get().uri("/api/v1/users/me")
                .exchange().expectStatus().isEqualTo(429).expectHeader().valueEquals("Retry-After", "20");

        // another user is not affected
        client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-b"))).get().uri("/api/v1/users/me")
                .exchange().expectStatus().isEqualTo(503);
    }
}
