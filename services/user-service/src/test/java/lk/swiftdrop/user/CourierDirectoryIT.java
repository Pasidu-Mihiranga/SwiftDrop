package lk.swiftdrop.user;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Courier directory against a real PostgreSQL (Flyway V3); Keycloak is replaced by a mock directory. */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pii.encryption-key=" + UserServiceApplicationIT.TEST_PII_KEY)
@AutoConfigureMockMvc
class CourierDirectoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private IdentityDirectory directory;

    private String courierId;

    private static RequestPostProcessor caller(String subject, String email, String... roles) {
        return jwt()
                .jwt(token -> token.subject(subject).claim("email", email)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @BeforeEach
    void keycloakHasOneCourierWhoNeverSignedIn() {
        courierId = UUID.randomUUID().toString();
        when(directory.usersWithRole(UserRole.COURIER)).thenReturn(List.of(
                new DirectoryUser(courierId, courierId + "@swiftdrop.lk", "Chamath", "Courier", true)));
    }

    @Test
    void anAdminGivesANewCourierDispatchDetailsAndDispatchSeesThem() throws Exception {
        mvc.perform(get("/api/v1/admin/couriers").with(caller("disp-1", "d@x.lk", "DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(courierId))
                .andExpect(jsonPath("$[0].profileComplete").value(false));

        mvc.perform(put("/api/v1/admin/couriers/" + courierId).with(caller("admin-1", "a@x.lk", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"THREE_WHEELER\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignable").value(true));

        mvc.perform(get("/api/v1/admin/couriers").param("zoneId", KANDY.toString()).param("assignable", "true")
                        .with(caller("disp-1", "d@x.lk", "DISPATCHER")))
                .andExpect(jsonPath("$[0].id").value(courierId))
                .andExpect(jsonPath("$[0].vehicleType").value("THREE_WHEELER"))
                .andExpect(jsonPath("$[0].maxDailyCapacity").value(30));

        assertThat(jdbc.queryForObject("SELECT full_name FROM user_profile WHERE id = ?", String.class, courierId))
                .isEqualTo("Chamath Courier");
        assertThat(jdbc.queryForList("SELECT role_code FROM user_role WHERE user_id = ?", String.class, courierId))
                .containsExactly("COURIER");
    }

    @Test
    void changingDetailsReplacesThemAndKeepsOneRow() throws Exception {
        String url = "/api/v1/admin/couriers/" + courierId;
        mvc.perform(put(url).with(caller("admin-1", "a@x.lk", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":20}"))
                .andExpect(status().isOk());

        mvc.perform(put(url).with(caller("admin-1", "a@x.lk", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"VAN\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":60,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("VAN"))
                .andExpect(jsonPath("$.assignable").value(false));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM courier_profile WHERE user_id = ?", Integer.class, courierId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM courier_profile WHERE user_id = ?", Long.class, courierId))
                .isEqualTo(1L);
    }

    @Test
    void theCourierCanStillSignInAfterAnAdminSetUpTheirDetails() throws Exception {
        mvc.perform(put("/api/v1/admin/couriers/" + courierId).with(caller("admin-1", "a@x.lk", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":20}"));

        mvc.perform(get("/api/v1/users/me").with(caller(courierId, courierId + "@swiftdrop.lk", "COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Chamath Courier"))
                .andExpect(jsonPath("$.roles[0]").value("COURIER"));
    }

    @Test
    void anIdThatKeycloakDoesNotListAsACourierIsNotFound() throws Exception {
        mvc.perform(put("/api/v1/admin/couriers/" + UUID.randomUUID()).with(caller("admin-1", "a@x.lk", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"BIKE\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_FOUND"));
    }
}
