package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Admins set a courier's vehicle, home zone, daily capacity and availability. */
@Service
@Transactional
public class CourierProfileService {

    private static final Logger log = LoggerFactory.getLogger(CourierProfileService.class);

    private final IdentityDirectory directory;
    private final UserProfileRepository userProfiles;
    private final CourierProfileRepository courierProfiles;

    public CourierProfileService(IdentityDirectory directory, UserProfileRepository userProfiles,
                                 CourierProfileRepository courierProfiles) {
        this.directory = directory;
        this.userProfiles = userProfiles;
        this.courierProfiles = courierProfiles;
    }

    /**
     * Saves the courier's dispatch details. The id must belong to a Keycloak user with the COURIER
     * role; anything else is reported as not found. A courier who has never signed in gets their
     * user profile created here from the Keycloak data, exactly as their first sign-in would.
     */
    public Courier updateProfile(String courierId, VehicleType vehicleType, UUID homeZoneId, int maxDailyCapacity,
                                 boolean active) {
        DirectoryUser user = directory.usersWithRole(UserRole.COURIER).stream()
                .filter(candidate -> candidate.id().equals(courierId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Courier", courierId));
        if (user.email() == null || user.email().isBlank()) {
            throw new BusinessRuleViolationException("COURIER_EMAIL_MISSING",
                    "Courier " + courierId + " has no e-mail address in Keycloak; add one before setting dispatch details");
        }
        userProfiles.createIfAbsent(UserProfile.firstSeen(user.id(), user.email(), user.displayName(), Set.of(UserRole.COURIER)));
        CourierProfile saved = courierProfiles.save(
                new CourierProfile(courierId, vehicleType, homeZoneId, maxDailyCapacity, active, null));
        log.info("Courier {} dispatch details updated: vehicle {}, zone {}, capacity {}, active {}",
                courierId, vehicleType, homeZoneId, maxDailyCapacity, active);
        return new Courier(user.id(), user.displayName(), user.email(), user.enabled(), Optional.of(saved));
    }
}
