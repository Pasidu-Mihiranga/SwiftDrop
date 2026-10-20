package lk.swiftdrop.__pkg__;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
{{#kafka}}import org.testcontainers.kafka.KafkaContainer;
{{/kafka}}import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the whole service against real infrastructure: Flyway migrations, JPA mapping, security, actuator. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class __Class__ServiceApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
{{#kafka}}
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");
{{/kafka}}
{{#redis}}
    @Container
    @ServiceConnection(name = "redis")
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);
{{/redis}}

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
        mvc.perform(get("/api/v1/__route__/meta/whoami")).andExpect(status().isUnauthorized());
    }
}
