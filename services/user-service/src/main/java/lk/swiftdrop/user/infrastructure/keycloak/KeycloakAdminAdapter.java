package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.domain.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Changes accounts through the Keycloak Admin REST API (needs manage-users): the adapter in the
 * "admin creates a courier account" design. Creating, enabling and re-roling happens in Keycloak;
 * user-service mirrors the result in users_db.
 */
public class KeycloakAdminAdapter implements IdentityAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);
    private static final long SET_PASSWORD_LINK_SECONDS = 24 * 60 * 60;
    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    private final KeycloakAdminClient keycloak;
    private final Map<UserRole, Map<String, Object>> roleRepresentations = new ConcurrentHashMap<>();

    public KeycloakAdminAdapter(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public List<DirectoryUser> allUsers() {
        return keycloak.pages("/admin/realms/{realm}/users?first={first}&max={max}&briefRepresentation=true").stream()
                .filter(json -> !String.valueOf(json.get("username")).startsWith(SERVICE_ACCOUNT_PREFIX))
                .map(KeycloakAdminClient::toDirectoryUser)
                .toList();
    }

    @Override
    public Optional<DirectoryUser> findUser(String userId) {
        try {
            Map<?, ?> json = keycloak.withToken(token -> keycloak.rest().get()
                    .uri("/admin/realms/{realm}/users/{id}", keycloak.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(Map.class));
            return Optional.ofNullable(json).map(KeycloakAdminClient::toDirectoryUser);
        } catch (HttpClientErrorException.NotFound unknown) {
            return Optional.empty();
        }
    }

    @Override
    public Set<UserRole> rolesOf(String userId) {
        List<Map<String, Object>> mappings = keycloak.onUser(userId, token -> keycloak.rest().get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", keycloak.realm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(KeycloakAdminClient.JSON_LIST));
        return UserRole.fromTokenRoles(mappings == null ? List.of()
                : mappings.stream().map(json -> String.valueOf(json.get("name"))).toList());
    }

    @Override
    public String createUser(String email, String fullName) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", email);
        user.put("email", email);
        user.put("firstName", firstName(fullName));
        user.put("lastName", lastName(fullName));
        user.put("enabled", true);
        user.put("emailVerified", false);
        ResponseEntity<Void> created;
        try {
            created = keycloak.withToken(token -> keycloak.rest().post()
                    .uri("/admin/realms/{realm}/users", keycloak.realm())
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.Conflict taken) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this e-mail address already exists");
        }
        URI location = created.getHeaders().getLocation();
        if (location == null) {
            throw new IdentityProviderUnavailableException("The identity provider did not return the new account's id");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public void grantRoles(String userId, Set<UserRole> roles) {
        changeRoles(userId, roles, HttpMethod.POST);
    }

    @Override
    public void revokeRoles(String userId, Set<UserRole> roles) {
        changeRoles(userId, roles, HttpMethod.DELETE);
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        keycloak.onUser(userId, token -> keycloak.rest().put()
                .uri("/admin/realms/{realm}/users/{id}", keycloak.realm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void endSessions(String userId) {
        keycloak.onUser(userId, token -> keycloak.rest().post()
                .uri("/admin/realms/{realm}/users/{id}/logout", keycloak.realm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void sendSetPasswordEmail(String userId) {
        keycloak.onUser(userId, token -> keycloak.rest().put()
                .uri("/admin/realms/{realm}/users/{id}/execute-actions-email?lifespan={lifespan}",
                        keycloak.realm(), userId, SET_PASSWORD_LINK_SECONDS)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of("UPDATE_PASSWORD"))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void deleteUser(String userId) {
        try {
            keycloak.withToken(token -> keycloak.rest().delete()
                    .uri("/admin/realms/{realm}/users/{id}", keycloak.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity());
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            log.debug("User {} was already gone from Keycloak", userId);
        }
    }

    private void changeRoles(String userId, Set<UserRole> roles, HttpMethod method) {
        if (roles.isEmpty()) {
            return;
        }
        List<Map<String, Object>> representations = roles.stream().map(this::roleRepresentation).toList();
        keycloak.onUser(userId, token -> keycloak.rest().method(method)
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", keycloak.realm(), userId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(representations)
                .retrieve()
                .toBodilessEntity());
    }

    /** Keycloak needs the role's id as well as its name to map it; roles never change, so they are cached. */
    private Map<String, Object> roleRepresentation(UserRole role) {
        return roleRepresentations.computeIfAbsent(role, missing -> {
            Map<?, ?> json = keycloak.withToken(token -> keycloak.rest().get()
                    .uri("/admin/realms/{realm}/roles/{role}", keycloak.realm(), missing.name())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(Map.class));
            if (json == null || json.get("id") == null) {
                throw new IdentityProviderUnavailableException("The identity provider does not know role " + missing);
            }
            return Map.of("id", json.get("id"), "name", missing.name());
        });
    }

    /** "Nimal Kumara Perera" splits into first "Nimal Kumara" and last "Perera"; one word is the first name only. */
    static String firstName(String fullName) {
        String name = fullName.strip();
        int lastSpace = name.lastIndexOf(' ');
        return lastSpace < 0 ? name : name.substring(0, lastSpace).strip();
    }

    static String lastName(String fullName) {
        String name = fullName.strip();
        int lastSpace = name.lastIndexOf(' ');
        return lastSpace < 0 ? "" : name.substring(lastSpace + 1);
    }
}
