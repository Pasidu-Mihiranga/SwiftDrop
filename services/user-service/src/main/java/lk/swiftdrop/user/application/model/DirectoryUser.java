package lk.swiftdrop.user.application.model;

/**
 * A user as Keycloak (the identity directory) knows them.
 *
 * @param id        Keycloak subject
 * @param email     sign-in e-mail, may be {@code null} for unusual accounts
 * @param firstName may be {@code null}
 * @param lastName  may be {@code null}
 * @param enabled   whether the account may sign in
 */
public record DirectoryUser(String id, String email, String firstName, String lastName, boolean enabled) {

    /** "First Last", or the e-mail, or the id when Keycloak has neither. */
    public String displayName() {
        String name = ((firstName == null ? "" : firstName.strip()) + " " + (lastName == null ? "" : lastName.strip())).strip();
        if (!name.isEmpty()) {
            return name;
        }
        return email != null && !email.isBlank() ? email : id;
    }
}
