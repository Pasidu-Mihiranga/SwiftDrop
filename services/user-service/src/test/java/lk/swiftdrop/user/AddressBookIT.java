package lk.swiftdrop.user;

import com.jayway.jsonpath.JsonPath;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The address book against a real PostgreSQL: Flyway V4, encryption at rest, the one-default index and ownership. */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pii.encryption-key=" + UserServiceApplicationIT.TEST_PII_KEY)
@AutoConfigureMockMvc
class AddressBookIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor person(String subject) {
        return jwt()
                .jwt(token -> token.subject(subject).claim("email", subject + "@example.lk")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static String address(String label, String line1, String district) {
        return "{\"label\":\"" + label + "\",\"line1\":\"" + line1 + "\",\"line2\":\"Near the temple\",\"city\":\""
                + district + "\",\"district\":\"" + district + "\",\"postalCode\":\"20000\",\"lat\":7.290572,\"lng\":80.633726}";
    }

    private String add(String subject, String body) throws Exception {
        String json = mvc.perform(post("/api/v1/users/me/addresses").with(person(subject))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void aFirstTimeCallerCanSaveAnAddressAndTheStreetIsEncrypted() throws Exception {
        String subject = UUID.randomUUID().toString();
        String id = add(subject, address("Home", "No. 12, Temple Road", "Kandy"));

        Map<String, Object> row = jdbc.queryForMap("SELECT line1_enc, line2_enc, city, is_default, lat FROM address WHERE id = ?::uuid", id);
        assertThat((String) row.get("line1_enc")).startsWith("v1:").doesNotContain("Temple");
        assertThat((String) row.get("line2_enc")).startsWith("v1:").doesNotContain("temple");
        assertThat(row.get("city")).isEqualTo("Kandy");
        assertThat(row.get("is_default")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE id = ?", Integer.class, subject)).isEqualTo(1);

        mvc.perform(get("/api/v1/users/me/addresses").with(person(subject)))
                .andExpect(jsonPath("$[0].line1").value("No. 12, Temple Road"))
                .andExpect(jsonPath("$[0].line2").value("Near the temple"))
                .andExpect(jsonPath("$[0].lat").value(7.290572))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void movingAndDeletingTheDefaultKeepsExactlyOne() throws Exception {
        String subject = UUID.randomUUID().toString();
        String home = add(subject, address("Home", "No. 12, Temple Road", "Kandy"));
        String office = add(subject, address("Office", "45 Galle Road", "Colombo"));

        mvc.perform(put("/api/v1/users/me/addresses/" + office).with(person(subject)).contentType(MediaType.APPLICATION_JSON)
                        .content(address("Office", "45 Galle Road", "Colombo").replace("}", ",\"isDefault\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(get("/api/v1/users/me/addresses").with(person(subject)))
                .andExpect(jsonPath("$[0].id").value(office))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        mvc.perform(delete("/api/v1/users/me/addresses/" + office).with(person(subject))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/addresses").with(person(subject)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(home))
                .andExpect(jsonPath("$[0].isDefault").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM address WHERE user_id = ? AND is_default", Integer.class, subject))
                .isEqualTo(1);
    }

    @Test
    void nobodyCanSeeOrChangeAnotherPersonsAddress() throws Exception {
        String owner = UUID.randomUUID().toString();
        String intruder = UUID.randomUUID().toString();
        String id = add(owner, address("Home", "No. 12, Temple Road", "Kandy"));

        mvc.perform(get("/api/v1/users/me/addresses").with(person(intruder)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/users/me/addresses/" + id).with(person(intruder)).contentType(MediaType.APPLICATION_JSON)
                        .content(address("Mine", "Stolen", "Galle")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mvc.perform(delete("/api/v1/users/me/addresses/" + id).with(person(intruder))).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT label FROM address WHERE id = ?::uuid", String.class, id)).isEqualTo("Home");
    }

    @Test
    void anAddressBookHoldsAtMostTen() throws Exception {
        String subject = UUID.randomUUID().toString();
        for (int i = 0; i < 10; i++) {
            add(subject, address("Place " + i, "Line " + i, "Kandy"));
        }

        mvc.perform(post("/api/v1/users/me/addresses").with(person(subject)).contentType(MediaType.APPLICATION_JSON)
                        .content(address("Eleventh", "Line 11", "Kandy")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }
}
