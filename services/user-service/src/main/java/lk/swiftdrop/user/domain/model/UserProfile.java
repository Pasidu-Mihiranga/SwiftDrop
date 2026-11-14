package lk.swiftdrop.user.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Profile data of one person. Identity (credentials, login, roles) lives in Keycloak; the id is the
 * Keycloak subject, so the two stores are linked by value and never by a foreign key.
 *
 * <p>Keycloak is the source of truth for the e-mail address and the roles, which are copied from
 * the token on every request. The full name and phone number belong to the user and are only
 * changed through {@link #withContactDetails}.
 *
 * @param id        Keycloak subject (JWT {@code sub})
 * @param email     lower-case e-mail address from the token
 * @param fullName  display name, 1 to 100 characters
 * @param phone     mobile number, or {@code null} when the user has not given one
 * @param status    whether an admin has disabled the account
 * @param roles     person roles mirrored from Keycloak
 * @param createdAt when the profile was first created; {@code null} before it is stored
 * @param updatedAt when the profile last changed; {@code null} before it is stored
 */
public record UserProfile(String id, String email, String fullName, PhoneNumber phone, ProfileStatus status,
                          Set<UserRole> roles, Instant createdAt, Instant updatedAt) {

    public static final int MAX_NAME_LENGTH = 100;

    public UserProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Profile id (Keycloak subject) is required");
        }
        email = normaliseEmail(email);
        fullName = normaliseName(fullName);
        status = status == null ? ProfileStatus.ACTIVE : status;
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    /**
     * The profile created on the first authenticated request. When the token has no name (for
     * example a user created without one), the part of the e-mail before {@code @} is used, which
     * the user can change later.
     */
    public static UserProfile firstSeen(String id, String email, String nameFromToken, Set<UserRole> roles) {
        String normalisedEmail = normaliseEmail(email);
        String name = nameFromToken == null || nameFromToken.isBlank()
                ? normalisedEmail.substring(0, normalisedEmail.indexOf('@'))
                : nameFromToken;
        return new UserProfile(id, email, name, null, ProfileStatus.ACTIVE, roles, null, null);
    }

    /** Copies the e-mail address and roles that Keycloak currently reports. */
    public UserProfile syncedWith(String tokenEmail, Set<UserRole> tokenRoles) {
        return new UserProfile(id, tokenEmail, fullName, phone, status, tokenRoles, createdAt, updatedAt);
    }

    /** The only change a user can make to their own profile. A {@code null} phone removes it. */
    public UserProfile withContactDetails(String newFullName, PhoneNumber newPhone) {
        return new UserProfile(id, email, newFullName, newPhone, status, roles, createdAt, updatedAt);
    }

    /** True when the stored e-mail or roles differ from what the token says. */
    public boolean differsFrom(String tokenEmail, Set<UserRole> tokenRoles) {
        return !email.equals(normaliseEmail(tokenEmail)) || !roles.equals(Set.copyOf(tokenRoles));
    }

    public Optional<PhoneNumber> phoneNumber() {
        return Optional.ofNullable(phone);
    }

    private static String normaliseEmail(String email) {
        if (email == null || email.isBlank() || email.indexOf('@') < 1) {
            throw new IllegalArgumentException("A valid e-mail address is required");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String normaliseName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Full name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return stripped;
    }
}
