package lk.swiftdrop.user.application.query;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The courier directory dispatch reads. Keycloak decides who is a courier, so a courier appears here
 * even before their first sign-in; the dispatch details come from users_db.
 */
@Service
@Transactional(readOnly = true)
public class CourierDirectoryService {

    private final IdentityDirectory directory;
    private final CourierProfileRepository courierProfiles;

    public CourierDirectoryService(IdentityDirectory directory, CourierProfileRepository courierProfiles) {
        this.directory = directory;
        this.courierProfiles = courierProfiles;
    }

    /**
     * @param homeZoneId only couriers whose home zone is this one; {@code null} for every zone
     * @param assignable {@code true} for couriers dispatch can assign to now, {@code false} for the rest,
     *                   {@code null} for everyone
     */
    public List<Courier> couriers(UUID homeZoneId, Boolean assignable) {
        List<DirectoryUser> users = directory.usersWithRole(UserRole.COURIER);
        Map<String, CourierProfile> profiles = courierProfiles.findByCourierIds(users.stream().map(DirectoryUser::id).toList());
        return users.stream()
                .map(user -> new Courier(user.id(), user.displayName(), user.email(), user.enabled(),
                        Optional.ofNullable(profiles.get(user.id()))))
                .filter(courier -> homeZoneId == null
                        || courier.profile().map(profile -> homeZoneId.equals(profile.homeZoneId())).orElse(false))
                .filter(courier -> assignable == null || courier.assignable() == assignable)
                .sorted(Comparator.comparing(Courier::fullName, String.CASE_INSENSITIVE_ORDER).thenComparing(Courier::id))
                .toList();
    }
}
