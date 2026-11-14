package lk.swiftdrop.user.application.command;

import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own profile. The profile is created just in time on the first request, so there is
 * no sign-up event or sync job between Keycloak and users_db.
 */
@Service
@Transactional
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository profiles;

    public UserProfileService(UserProfileRepository profiles) {
        this.profiles = profiles;
    }

    /**
     * Returns the caller's profile, creating it on the first call. On later calls the e-mail
     * address and roles are refreshed from the token, because Keycloak is their source of truth;
     * the name and phone the user chose are left alone.
     */
    public UserProfile getOrCreate(IdentityClaims caller) {
        if (profiles.createIfAbsent(UserProfile.firstSeen(caller.subject(), caller.email(), caller.name(), caller.roles()))) {
            log.info("Created profile for user {} with roles {}", caller.subject(), caller.roles());
        }
        UserProfile profile = profiles.findById(caller.subject())
                .orElseThrow(() -> new IllegalStateException("Profile " + caller.subject() + " missing right after creation"));
        if (profile.differsFrom(caller.email(), caller.roles())) {
            profile = profiles.save(profile.syncedWith(caller.email(), caller.roles()));
        }
        return profile;
    }

    /** Replaces the caller's name and phone number. A {@code null} phone removes the stored number. */
    public UserProfile updateContactDetails(IdentityClaims caller, String fullName, PhoneNumber phone) {
        UserProfile current = getOrCreate(caller);
        return profiles.save(current.withContactDetails(fullName, phone));
    }
}
