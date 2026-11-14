package lk.swiftdrop.user.application.port.out;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.domain.model.UserRole;

import java.util.List;

/**
 * Read access to the identity provider (Keycloak). Implemented by an adapter over the Keycloak Admin
 * REST API, so the application layer never sees HTTP or Keycloak types.
 */
public interface IdentityDirectory {

    /**
     * Every user who holds the realm role, enabled or not.
     *
     * @throws lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException when Keycloak cannot be reached
     */
    List<DirectoryUser> usersWithRole(UserRole role);
}
