package lk.swiftdrop.user;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin user management against a real PostgreSQL; Keycloak is replaced by a mock. */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pii.encryption-key=" + UserServiceApplicationIT.TEST_PII_KEY)
@AutoConfigureMockMvc
class AdminUserIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private IdentityAdmin admin;

    /** Role members for the search; a Mockito mock answers an empty list, i.e. nobody holds a role directly. */
    @MockitoBean
    private IdentityDirectory directory;

    private static RequestPostProcessor adminCaller() {
        return jwt()
                .jwt(token -> token.subject("admin-1").claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    @Test
    void creatingACourierStoresTheProfileRoleEncryptedPhoneAndDispatchDetails() throws Exception {
        String id = UUID.randomUUID().toString();
        when(admin.createUser(eq(id + "@swiftdrop.lk"), anyString())).thenReturn(id);

        mvc.perform(post("/api/v1/admin/users").with(adminCaller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + id + "@swiftdrop.lk\",\"fullName\":\"Kasun Silva\",\"phone\":\"0771234567\","
                                + "\"role\":\"COURIER\",\"vehicleType\":\"VAN\",\"homeZoneId\":\"" + KANDY + "\",\"maxDailyCapacity\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.invitationSent").value(true));

        assertThat(jdbc.queryForObject("SELECT full_name FROM user_profile WHERE id = ?", String.class, id)).isEqualTo("Kasun Silva");
        assertThat(jdbc.queryForObject("SELECT phone_enc FROM user_profile WHERE id = ?", String.class, id))
                .startsWith("v1:").doesNotContain("771234567");
        assertThat(jdbc.queryForList("SELECT role_code FROM user_role WHERE user_id = ? ORDER BY role_code", String.class, id))
                .containsExactly("COURIER", "CUSTOMER");
        assertThat(jdbc.queryForObject("SELECT max_daily_capacity FROM courier_profile WHERE user_id = ?", Integer.class, id))
                .isEqualTo(40);
        verify(admin).grantRoles(id, Set.of(UserRole.COURIER));
        verify(admin).sendSetPasswordEmail(id);
    }

    @Test
    void anEmailAlreadyInUsersDbUndoesTheKeycloakAccount() throws Exception {
        String existing = UUID.randomUUID().toString();
        String email = existing + "@swiftdrop.lk";
        jdbc.update("INSERT INTO user_profile (id, email, full_name) VALUES (?, ?, 'Old Account')", existing, email);
        when(admin.createUser(eq(email), anyString())).thenReturn("fresh-keycloak-id");

        mvc.perform(post("/api/v1/admin/users").with(adminCaller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Dilan\",\"role\":\"DISPATCHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_LINKED"));

        verify(admin).deleteUser("fresh-keycloak-id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE id = 'fresh-keycloak-id'", Integer.class)).isZero();
    }

    @Test
    void disablingAnAccountIsMirroredIntoTheProfile() throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_profile (id, email, full_name) VALUES (?, ?, 'Chamath')", id, id + "@swiftdrop.lk");
        when(admin.findUser(id)).thenReturn(Optional.of(new DirectoryUser(id, id + "@swiftdrop.lk", "Chamath", null, true)));
        when(admin.rolesOf(id)).thenReturn(Set.of(UserRole.COURIER));

        mvc.perform(patch("/api/v1/admin/users/" + id).with(adminCaller()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(jdbc.queryForObject("SELECT status FROM user_profile WHERE id = ?", String.class, id)).isEqualTo("DISABLED");
        verify(admin).setEnabled(id, false);
        verify(admin).endSessions(id);
    }

    @Test
    void searchReadsKeycloak() throws Exception {
        when(admin.allUsers()).thenReturn(List.of(new DirectoryUser("x-1", "x@swiftdrop.lk", "Xavier", null, true)));

        mvc.perform(get("/api/v1/admin/users").with(adminCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].email").value("x@swiftdrop.lk"));
    }
}
