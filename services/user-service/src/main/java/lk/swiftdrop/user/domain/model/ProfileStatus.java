package lk.swiftdrop.user.domain.model;

/** Whether an admin has disabled the account. Keycloak also blocks sign-in for disabled users. */
public enum ProfileStatus {
    ACTIVE, DISABLED
}
