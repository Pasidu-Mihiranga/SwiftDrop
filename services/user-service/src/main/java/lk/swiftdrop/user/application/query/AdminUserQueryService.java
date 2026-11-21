package lk.swiftdrop.user.application.query;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.model.ManagedUser;
import lk.swiftdrop.user.application.model.UserPage;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User search for admins. Keycloak holds every account, so this reads Keycloak rather than
 * users_db (which only knows people who have signed in at least once). Roles come from one call
 * per role instead of one call per user, which keeps a search at six Keycloak requests.
 */
@Service
public class AdminUserQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final IdentityAdmin admin;
    private final IdentityDirectory directory;

    public AdminUserQueryService(IdentityAdmin admin, IdentityDirectory directory) {
        this.admin = admin;
        this.directory = directory;
    }

    /**
     * @param search  part of the name or e-mail, case-insensitive; blank for everyone
     * @param role    only users holding this role; {@code null} for every role
     * @param enabled only enabled ({@code true}) or disabled ({@code false}) accounts; {@code null} for both
     */
    public UserPage search(String search, UserRole role, Boolean enabled, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        Map<String, Set<UserRole>> directRoles = new HashMap<>();
        for (UserRole candidate : UserRole.values()) {
            for (DirectoryUser member : directory.usersWithRole(candidate)) {
                directRoles.computeIfAbsent(member.id(), id -> EnumSet.noneOf(UserRole.class)).add(candidate);
            }
        }
        String needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        List<ManagedUser> matches = admin.allUsers().stream()
                .map(user -> ManagedUser.of(user, directRoles.getOrDefault(user.id(), Set.of())))
                .filter(user -> needle.isEmpty() || contains(user.fullName(), needle) || contains(user.email(), needle))
                .filter(user -> role == null || user.roles().contains(role))
                .filter(user -> enabled == null || user.enabled() == enabled)
                .sorted(Comparator.comparing(ManagedUser::fullName, String.CASE_INSENSITIVE_ORDER).thenComparing(ManagedUser::id))
                .toList();
        int from = Math.min(matches.size(), safePage * safeSize);
        int to = Math.min(matches.size(), from + safeSize);
        return new UserPage(matches.subList(from, to), matches.size(), safePage, safeSize);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
