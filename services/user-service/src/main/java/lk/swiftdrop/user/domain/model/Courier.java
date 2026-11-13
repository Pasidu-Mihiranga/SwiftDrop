package lk.swiftdrop.user.domain.model;

import java.util.Optional;

/**
 * A courier as dispatch sees them: the identity from Keycloak plus the dispatch details, which an
 * admin may not have filled in yet.
 *
 * @param id       Keycloak subject
 * @param fullName first and last name from Keycloak (the e-mail when Keycloak has no name)
 * @param email    sign-in e-mail
 * @param enabled  whether the Keycloak account may sign in
 * @param profile  vehicle, zone and capacity, empty until an admin sets them
 */
public record Courier(String id, String fullName, String email, boolean enabled, Optional<CourierProfile> profile) {

    public Courier {
        profile = profile == null ? Optional.empty() : profile;
    }

    /** Dispatchers can only assign parcels to an enabled courier with complete, active details. */
    public boolean assignable() {
        return enabled && profile.map(CourierProfile::active).orElse(false);
    }
}
