package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The account-changing half of the Keycloak adapter, against a mock Keycloak. */
class KeycloakAdminAdapterWriteTest {

    private static final String KC = "http://kc";
    private static final String USERS = KC + "/admin/realms/swiftdrop/users";
    private static final String TOKEN = "{\"access_token\":\"t-1\",\"expires_in\":300}";

    private MockRestServiceServer keycloak;
    private KeycloakAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(KC);
        keycloak = MockRestServiceServer.bindTo(builder).build();
        adapter = new KeycloakAdminAdapter(new KeycloakAdminClient(builder.build(), "swiftdrop", "user-service-admin", "s3cret",
                Clock.fixed(Instant.parse("2026-11-22T08:00:00Z"), ZoneOffset.UTC)));
        keycloak.expect(once(), requestTo(KC + "/realms/swiftdrop/protocol/openid-connect/token"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
    }

    @Test
    void createsAnEnabledAccountWithTheEmailAsUsernameAndReturnsItsId() {
        HttpHeaders location = new HttpHeaders();
        location.setLocation(URI.create(USERS + "/new-id-123"));
        keycloak.expect(requestTo(USERS)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"username\":\"kasun@x.lk\",\"email\":\"kasun@x.lk\",\"firstName\":\"Kasun\","
                        + "\"lastName\":\"Silva\",\"enabled\":true,\"emailVerified\":false}"))
                .andRespond(withStatus(HttpStatus.CREATED).headers(location));

        assertThat(adapter.createUser("kasun@x.lk", "Kasun Silva")).isEqualTo("new-id-123");
        keycloak.verify();
    }

    @Test
    void aTakenEmailIsAConflict() {
        keycloak.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> adapter.createUser("kasun@x.lk", "Kasun"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void aCreatedAccountWithoutALocationIsReported() {
        keycloak.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CREATED));

        assertThatThrownBy(() -> adapter.createUser("kasun@x.lk", "Kasun"))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void grantsAndRevokesRolesLookingEachRoleUpOnlyOnce() {
        keycloak.expect(once(), requestTo(KC + "/admin/realms/swiftdrop/roles/COURIER"))
                .andRespond(withSuccess("{\"id\":\"role-courier\",\"name\":\"COURIER\"}", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(USERS + "/u-1/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"role-courier\",\"name\":\"COURIER\"}]"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        keycloak.expect(requestTo(USERS + "/u-1/role-mappings/realm")).andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("[{\"id\":\"role-courier\",\"name\":\"COURIER\"}]"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        adapter.grantRoles("u-1", Set.of(UserRole.COURIER));
        adapter.revokeRoles("u-1", Set.of(UserRole.COURIER));
        adapter.grantRoles("u-1", Set.of());
        keycloak.verify();
    }

    @Test
    void anUnknownRoleIsReported() {
        keycloak.expect(requestTo(KC + "/admin/realms/swiftdrop/roles/ADMIN")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.grantRoles("u-1", Set.of(UserRole.ADMIN)))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void disablesSignsOutAndSendsTheSetPasswordEmail() {
        keycloak.expect(requestTo(USERS + "/u-1")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"enabled\":false}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        keycloak.expect(requestTo(USERS + "/u-1/logout")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        keycloak.expect(requestTo(USERS + "/u-1/execute-actions-email?lifespan=86400")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("[\"UPDATE_PASSWORD\"]"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        adapter.setEnabled("u-1", false);
        adapter.endSessions("u-1");
        adapter.sendSetPasswordEmail("u-1");
        keycloak.verify();
    }

    @Test
    void anUnknownUserIsNotFound() {
        keycloak.expect(requestTo(USERS + "/nobody")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.setEnabled("nobody", true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nobody");
    }

    @Test
    void readsOneUserAndTheirRoles() {
        keycloak.expect(requestTo(USERS + "/u-1"))
                .andRespond(withSuccess("{\"id\":\"u-1\",\"email\":\"a@x.lk\",\"firstName\":\"Amara\",\"enabled\":true}", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(USERS + "/u-1/role-mappings/realm"))
                .andRespond(withSuccess("[{\"name\":\"ADMIN\"},{\"name\":\"default-roles-swiftdrop\"}]", MediaType.APPLICATION_JSON));
        keycloak.expect(requestTo(USERS + "/gone")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.findUser("u-1")).contains(new DirectoryUser("u-1", "a@x.lk", "Amara", null, true));
        assertThat(adapter.rolesOf("u-1")).containsExactly(UserRole.ADMIN);
        assertThat(adapter.findUser("gone")).isEmpty();
    }

    @Test
    void listsPeopleButNotServiceAccounts() {
        keycloak.expect(requestTo(USERS + "?first=0&max=200&briefRepresentation=true"))
                .andRespond(withSuccess("[{\"id\":\"u-1\",\"username\":\"a@x.lk\",\"email\":\"a@x.lk\",\"enabled\":true},"
                        + "{\"id\":\"sa-1\",\"username\":\"service-account-user-service-admin\",\"enabled\":true}]", MediaType.APPLICATION_JSON));

        assertThat(adapter.allUsers()).extracting(DirectoryUser::id).containsExactly("u-1");
    }

    @Test
    void deletingAnAlreadyMissingUserIsFine() {
        keycloak.expect(requestTo(USERS + "/u-1")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        adapter.deleteUser("u-1");
        keycloak.verify();
    }

    @Test
    void splitsAFullNameIntoFirstAndLast() {
        assertThat(KeycloakAdminAdapter.firstName("Nimal Kumara Perera")).isEqualTo("Nimal Kumara");
        assertThat(KeycloakAdminAdapter.lastName("Nimal Kumara Perera")).isEqualTo("Perera");
        assertThat(KeycloakAdminAdapter.firstName(" Madonna ")).isEqualTo("Madonna");
        assertThat(KeycloakAdminAdapter.lastName("Madonna")).isEmpty();
    }
}
