package lk.swiftdrop.user;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.user.application.command.UserProfileService;
import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profiles against a real PostgreSQL: Flyway V2, JPA mapping, encryption at rest and the just-in-time insert. */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pii.encryption-key=" + UserServiceApplicationIT.TEST_PII_KEY)
@AutoConfigureMockMvc
class UserProfileIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserProfileService service;

    private static RequestPostProcessor person(String subject, String email, String... roles) {
        return jwt()
                .jwt(token -> token.subject(subject).claim("email", email).claim("name", "Nimal Perera")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static String newSubject() {
        return UUID.randomUUID().toString();
    }

    @Test
    void theFirstRequestCreatesTheProfileAndMirrorsTheRoles() throws Exception {
        String subject = newSubject();

        mvc.perform(get("/api/v1/users/me").with(person(subject, "Nimal@Example.lk", "CUSTOMER", "offline_access")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subject))
                .andExpect(jsonPath("$.email").value("nimal@example.lk"))
                .andExpect(jsonPath("$.fullName").value("Nimal Perera"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE id = ?", Integer.class, subject)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT role_code FROM user_role WHERE user_id = ?", String.class, subject))
                .containsExactly("CUSTOMER");
    }

    @Test
    void laterRequestsFollowRoleChangesMadeInKeycloak() throws Exception {
        String subject = newSubject();
        String email = subject + "@example.lk";
        mvc.perform(get("/api/v1/users/me").with(person(subject, email, "CUSTOMER"))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/me").with(person(subject, email, "COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("COURIER"));

        assertThat(jdbc.queryForList("SELECT role_code FROM user_role WHERE user_id = ?", String.class, subject))
                .containsExactly("COURIER");
    }

    @Test
    void thePhoneIsStoredEncryptedAndReturnedInPlainText() throws Exception {
        String subject = newSubject();
        String email = subject + "@example.lk";

        mvc.perform(put("/api/v1/users/me").with(person(subject, email, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nimal Perera\",\"phone\":\"0771234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+94771234567"));

        String stored = jdbc.queryForObject("SELECT phone_enc FROM user_profile WHERE id = ?", String.class, subject);
        assertThat(stored).startsWith("v1:").doesNotContain("771234567");

        mvc.perform(get("/api/v1/users/me").with(person(subject, email, "CUSTOMER")))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.fullName").value("Nimal Perera"));

        mvc.perform(put("/api/v1/users/me").with(person(subject, email, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nimal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT phone_enc FROM user_profile WHERE id = ?", String.class, subject)).isNull();
    }

    @Test
    void simultaneousFirstRequestsCreateExactlyOneProfile() throws Exception {
        String subject = newSubject();
        IdentityClaims caller = new IdentityClaims(subject, subject + "@example.lk", "Nimal", Set.of(UserRole.CUSTOMER));
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> service.getOrCreate(caller).id());
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<String> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(subject);
            }
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE id = ?", Integer.class, subject)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id = ?", Integer.class, subject)).isEqualTo(1);
    }

    @Test
    void anEmailThatBelongsToAnotherProfileIsAConflict() throws Exception {
        String email = newSubject() + "@example.lk";
        mvc.perform(get("/api/v1/users/me").with(person(newSubject(), email, "CUSTOMER"))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/me").with(person(newSubject(), email.toUpperCase(), "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_LINKED"));
    }
}
