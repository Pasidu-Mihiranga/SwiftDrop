package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;

import java.util.List;

/** Reads who holds a realm role, e.g. every courier for the dispatch directory (needs view-users and view-realm). */
public class KeycloakIdentityDirectory implements IdentityDirectory {

    private final KeycloakAdminClient keycloak;

    public KeycloakIdentityDirectory(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public List<DirectoryUser> usersWithRole(UserRole role) {
        return keycloak.pages("/admin/realms/{realm}/roles/{role}/users?first={first}&max={max}&briefRepresentation=true",
                role.name()).stream().map(KeycloakAdminClient::toDirectoryUser).toList();
    }
}
