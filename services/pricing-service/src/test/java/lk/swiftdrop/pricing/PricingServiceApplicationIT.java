package lk.swiftdrop.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole service against real infrastructure: Flyway migrations, JPA mapping, security, actuator. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PricingServiceApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void startsAndReportsHealthy() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsBusinessEndpoints() throws Exception {
        mvc.perform(get("/api/v1/pricing/meta/whoami")).andExpect(status().isUnauthorized());
    }

    @Test
    void issuesAPublicQuoteAndStoresTheReturnedSnapshotWithExpiry() throws Exception {
        String response = mvc.perform(post("/api/v1/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromDistrict\":\"Colombo\",\"toDistrict\":\"Kandy\",\"weightKg\":2,\"sizeClass\":\"M\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.fromZone").value("WP"))
                .andExpect(jsonPath("$.toZone").value("CP"))
                .andReturn().getResponse().getContentAsString();

        String key = "pricing:quote:" + mapper.readTree(response).get("quoteId").asText();
        assertThat(mapper.readTree(redis.opsForValue().get(key))).isEqualTo(mapper.readTree(response));
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 900L);

        String quoteId = mapper.readTree(response).get("quoteId").asText();
        mvc.perform(get("/api/v1/pricing/quotes/" + quoteId))
                .andExpect(status().isUnauthorized());
        String lookedUp = mvc.perform(get("/api/v1/pricing/quotes/" + quoteId + "?weightKg=99&fee=0")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(lookedUp)).isEqualTo(mapper.readTree(response));

        redis.delete(key);
        mvc.perform(get("/api/v1/pricing/quotes/" + quoteId).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }
}
