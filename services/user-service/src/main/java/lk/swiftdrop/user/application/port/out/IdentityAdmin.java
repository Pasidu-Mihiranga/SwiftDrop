package lk.swiftdrop.user.application.port.out;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.domain.model.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Changes accounts in the identity provider (Keycloak). Keycloak owns credentials and sign-in, so
 * creating, enabling and re-roling a person happens there; user-service only mirrors the result.
 * Every method reports Keycloak being unreachable as
 * {@link lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException}.
 */
public interface IdentityAdmin {

    /** Every person account (service accounts excluded). */
    List<DirectoryUser> allUsers();

    Optional<DirectoryUser> findUser(String userId);

    /** The person roles the user holds directly. */
    Set<UserRole> rolesOf(String userId);

    /**
     * Creates an enabled account with the e-mail as username and no password.
     *
     * @return the new Keycloak user id
     * @throws lk.swiftdrop.common.web.error.ConflictException EMAIL_ALREADY_REGISTERED when the e-mail is taken
     */
    String createUser(String email, String fullName);

    void grantRoles(String userId, Set<UserRole> roles);

    void revokeRoles(String userId, Set<UserRole> roles);

    void setEnabled(String userId, boolean enabled);

    /** Signs the user out everywhere, e.g. right after disabling the account. */
    void endSessions(String userId);

    /** Keycloak e-mails a "set your password" link, so SwiftDrop never handles a staff password. */
    void sendSetPasswordEmail(String userId);

    /** Removes an account again; used to undo a creation that could not be completed. */
    void deleteUser(String userId);
}
