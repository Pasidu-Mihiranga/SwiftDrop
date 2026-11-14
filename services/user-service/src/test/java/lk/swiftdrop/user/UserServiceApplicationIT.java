package lk.swiftdrop.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole service against real infrastructure: Flyway migrations, JPA mapping, security, actuator. */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pii.encryption-key=" + UserServiceApplicationIT.TEST_PII_KEY)
@AutoConfigureMockMvc
class UserServiceApplicationIT {

    /** base64 of 32 ASCII bytes; a test-only key, never used outside tests. */
    static final String TEST_PII_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    @Autowired
    private MockMvc mvc;

    @Test
    void startsAndReportsHealthy() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectsBusinessEndpoints() throws Exception {
        mvc.perform(get("/api/v1/users/meta/whoami")).andExpect(status().isUnauthorized());
    }
}
