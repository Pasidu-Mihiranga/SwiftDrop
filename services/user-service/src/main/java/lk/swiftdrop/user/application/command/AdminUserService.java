package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.model.ManagedUser;
import lk.swiftdrop.user.application.model.NewStaffAccount;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.ProfileStatus;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Admin user management: create staff accounts, enable or disable accounts and change staff roles.
 * Keycloak is changed first (it owns sign-in); users_db mirrors the result.
 *
 * <p>Two rules keep the team from locking itself out: an admin can neither disable their own
 * account nor take away their own ADMIN role.
 */
@Service
@Transactional
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final IdentityAdmin admin;
    private final UserProfileRepository profiles;
    private final CourierProfileRepository courierProfiles;

    public AdminUserService(IdentityAdmin admin, UserProfileRepository profiles, CourierProfileRepository courierProfiles) {
        this.admin = admin;
        this.profiles = profiles;
        this.courierProfiles = courierProfiles;
    }

    /** The account and whether Keycloak accepted the "set your password" e-mail. */
    public record CreatedAccount(ManagedUser user, boolean invitationSent) {
    }

    /**
     * Creates the Keycloak account with the staff role, then the profile (and courier details) in
     * users_db. If the database part fails, the Keycloak account is deleted again so no half-made
     * account is left behind. The set-password e-mail goes out last; if it fails the account still
     * exists and the admin can send it again.
     */
    public CreatedAccount create(NewStaffAccount request) {
        if (!request.role().isStaff()) {
            throw new BadRequestException("ROLE_NOT_ASSIGNABLE", "Admins can create COURIER, DISPATCHER or ADMIN accounts");
        }
        if (request.courier() != null && request.role() != UserRole.COURIER) {
            throw new BadRequestException("COURIER_DETAILS_FOR_COURIERS_ONLY", "Vehicle, zone and capacity apply to couriers only");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String userId = admin.createUser(email, request.fullName());
        try {
            admin.grantRoles(userId, Set.of(request.role()));
            Set<UserRole> roles = ManagedUser.effectiveRoles(Set.of(request.role()));
            profiles.createIfAbsent(UserProfile.firstSeen(userId, email, request.fullName(), roles));
            if (request.phone() != null) {
                UserProfile created = profiles.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Profile " + userId + " missing right after creation"));
                profiles.save(created.withContactDetails(request.fullName(), request.phone()));
            }
            if (request.courier() != null) {
                NewStaffAccount.CourierDetails courier = request.courier();
                courierProfiles.save(new CourierProfile(userId, courier.vehicleType(), courier.homeZoneId(),
                        courier.maxDailyCapacity(), true, null));
            }
        } catch (RuntimeException failed) {
            log.warn("Creating account {} failed after Keycloak made it; removing it from Keycloak again", userId);
            try {
                admin.deleteUser(userId);
            } catch (RuntimeException cleanupFailed) {
                log.error("Could not remove half-created Keycloak account {}; delete it in the Keycloak console", userId);
            }
            throw failed;
        }
        log.info("Admin created {} account {}", request.role(), userId);
        boolean invitationSent = sendInvitation(userId);
        return new CreatedAccount(new ManagedUser(userId, email, request.fullName().strip(), true,
                ManagedUser.effectiveRoles(Set.of(request.role()))), invitationSent);
    }

    /**
     * Enables or disables the account and/or replaces its staff roles. {@code null} leaves that part
     * alone. Customer and merchant roles are never touched here.
     *
     * @param callerId   the admin making the change
     * @param staffRoles the complete set of staff roles the user should hold
     */
    public ManagedUser update(String callerId, String userId, Boolean enabled, Set<UserRole> staffRoles) {
        DirectoryUser user = admin.findUser(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        if (staffRoles != null && staffRoles.stream().anyMatch(role -> !role.isStaff())) {
            throw new BadRequestException("ROLE_NOT_ASSIGNABLE", "Only COURIER, DISPATCHER and ADMIN can be changed here");
        }
        if (userId.equals(callerId)) {
            if (Boolean.FALSE.equals(enabled)) {
                throw new BusinessRuleViolationException("CANNOT_DISABLE_YOURSELF", "You cannot disable your own account");
            }
            if (staffRoles != null && !staffRoles.contains(UserRole.ADMIN)) {
                throw new BusinessRuleViolationException("CANNOT_REMOVE_OWN_ADMIN", "You cannot remove your own ADMIN role");
            }
        }

        Set<UserRole> direct = EnumSet.noneOf(UserRole.class);
        direct.addAll(admin.rolesOf(userId));
        if (staffRoles != null) {
            Set<UserRole> currentStaff = EnumSet.noneOf(UserRole.class);
            direct.stream().filter(UserRole::isStaff).forEach(currentStaff::add);
            Set<UserRole> toGrant = EnumSet.noneOf(UserRole.class);
            toGrant.addAll(staffRoles);
            toGrant.removeAll(currentStaff);
            Set<UserRole> toRevoke = EnumSet.noneOf(UserRole.class);
            toRevoke.addAll(currentStaff);
            toRevoke.removeAll(staffRoles);
            admin.grantRoles(userId, toGrant);
            admin.revokeRoles(userId, toRevoke);
            direct.addAll(toGrant);
            direct.removeAll(toRevoke);
        }
        boolean nowEnabled = enabled == null ? user.enabled() : enabled;
        if (enabled != null && enabled != user.enabled()) {
            admin.setEnabled(userId, enabled);
            if (!enabled) {
                admin.endSessions(userId);
            }
        }

        Set<UserRole> effective = ManagedUser.effectiveRoles(direct);
        profiles.findById(userId).ifPresent(profile -> profiles.save(profile
                .withStatus(nowEnabled ? ProfileStatus.ACTIVE : ProfileStatus.DISABLED)
                .syncedWith(profile.email(), effective)));
        log.info("Admin {} updated user {}: enabled {}, roles {}", callerId, userId, nowEnabled, effective);
        return new ManagedUser(userId, user.email(), user.displayName(), nowEnabled, effective);
    }

    /** Sends the set-password e-mail again, e.g. when the first one expired. */
    public void resendInvitation(String userId) {
        admin.findUser(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        admin.sendSetPasswordEmail(userId);
    }

    private boolean sendInvitation(String userId) {
        try {
            admin.sendSetPasswordEmail(userId);
            return true;
        } catch (RuntimeException failed) {
            log.warn("Account {} was created but the set-password e-mail could not be sent: {}", userId, failed.getMessage());
            return false;
        }
    }
}
