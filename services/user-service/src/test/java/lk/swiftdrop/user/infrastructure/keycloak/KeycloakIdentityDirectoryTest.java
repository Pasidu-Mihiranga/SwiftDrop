package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakIdentityDirectoryTest {

    private static final String TOKEN_URL = "http://kc/realms/swiftdrop/protocol/openid-connect/token";
    private static final String COURIERS_PAGE_1 = "http://kc/admin/realms/swiftdrop/roles/COURIER/users?first=0&max=200&briefRepresentation=true";
    private static final String COURIERS_PAGE_2 = "http://kc/admin/realms/swiftdrop/roles/COURIER/users?first=200&max=200&briefRepresentation=true";
    private static final String TWO_COURIERS = """
            [{"id":"c-1","email":"chamath@swiftdrop.lk","firstName":"Chamath","lastName":"Courier","enabled":true,"username":"chamath"},
             {"id":"c-2","email":"kasun@swiftdrop.lk","enabled":false}]""";

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MockRestServiceServer keycloak;
    private MovableClock clock;
    private KeycloakIdentityDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kc");
        keycloak = MockRestServiceServer.bindTo(builder).build();
        clock = new MovableClock();
        directory = new KeycloakIdentityDirectory(builder.build(), "swiftdrop", "user-service-admin", "s3cret", clock);
    }

    private static String token(String value) {
        return "{\"access_token\":\"" + value + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
    }

    @Test
    void signsInWithClientCredentialsOnceAndReadsTheRoleMembers() {
        keycloak.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials", "client_id", "user-service-admin", "client_secret", "s3cret")))
                .andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(twice(), requestTo(COURIERS_PAGE_1))
                .andExpect(header("Authorization", "Bearer t-1"))
                .andRespond(withSuccess(TWO_COURIERS, MediaType.APPLICATION_JSON));

        List<DirectoryUser> first = directory.usersWithRole(UserRole.COURIER);
        directory.usersWithRole(UserRole.COURIER);

        assertThat(first).containsExactly(
                new DirectoryUser("c-1", "chamath@swiftdrop.lk", "Chamath", "Courier", true),
                new DirectoryUser("c-2", "kasun@swiftdrop.lk", null, null, false));
        keycloak.verify();
    }

    @Test
    void getsANewTokenShortlyBeforeTheOldOneExpires() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andExpect(header("Authorization", "Bearer t-1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-2"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andExpect(header("Authorization", "Bearer t-2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        directory.usersWithRole(UserRole.COURIER);
        clock.now = clock.now.plusSeconds(271);
        directory.usersWithRole(UserRole.COURIER);

        keycloak.verify();
    }

    @Test
    void readsEveryPage() {
        String fullPage = IntStream.range(0, 200)
                .mapToObj(i -> "{\"id\":\"c-" + i + "\",\"email\":\"c" + i + "@x.lk\",\"enabled\":true}")
                .collect(Collectors.joining(",", "[", "]"));
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_2)).andRespond(withSuccess(
                "[{\"id\":\"c-last\",\"email\":\"last@x.lk\",\"enabled\":true}]", MediaType.APPLICATION_JSON));

        assertThat(directory.usersWithRole(UserRole.COURIER)).hasSize(201).last()
                .extracting(DirectoryUser::id).isEqualTo("c-last");
    }

    @Test
    void retriesOnceWithAFreshTokenWhenKeycloakDropsTheOldOne() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-2"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andExpect(header("Authorization", "Bearer t-2"))
                .andRespond(withSuccess(TWO_COURIERS, MediaType.APPLICATION_JSON));

        assertThat(directory.usersWithRole(UserRole.COURIER)).hasSize(2);
        keycloak.verify();
    }

    @Test
    void givesUpAfterASecondRejection() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-2"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void reportsKeycloakBeingDownAsUnavailable() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("not reachable");
    }

    @Test
    void reportsAWrongClientSecretAsUnavailable() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void reportsMissingPermissionsAsUnavailable() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("refused");
    }

    @Test
    void reportsAKeycloakErrorAndAnEmptyTokenAsUnavailable() {
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("no access token");

        keycloak.reset();
        keycloak.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(token("t-1"), MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(COURIERS_PAGE_1)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> directory.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("not reachable");
    }

    @Test
    void doesNotCallKeycloakWithoutAClientSecret() {
        KeycloakIdentityDirectory unconfigured = new KeycloakIdentityDirectory(
                RestClient.builder().baseUrl("http://kc").build(), "swiftdrop", "user-service-admin", " ", clock);

        assertThatThrownBy(() -> unconfigured.usersWithRole(UserRole.COURIER))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("not configured");
    }
}
