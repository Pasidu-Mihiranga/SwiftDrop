package lk.swiftdrop.user.application.port.out;

import lk.swiftdrop.user.domain.model.UserProfile;

import java.util.Optional;

/** Stores profiles and their mirrored roles. */
public interface UserProfileRepository {

    Optional<UserProfile> findById(String id);

    /**
     * Inserts the profile unless one with the same id already exists. Two first requests from the
     * same user can arrive together, so this must be safe to call twice.
     *
     * @return {@code true} when this call created the profile
     */
    boolean createIfAbsent(UserProfile profile);

    /** Updates an existing profile: e-mail, name, phone and roles. */
    UserProfile save(UserProfile profile);
}
