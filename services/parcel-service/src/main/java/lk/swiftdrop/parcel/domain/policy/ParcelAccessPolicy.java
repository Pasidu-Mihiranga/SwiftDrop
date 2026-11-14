package lk.swiftdrop.parcel.domain.policy;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.parcel.infrastructure.ParcelEntity;
import org.springframework.stereotype.Component;

/** Deliberately reports a foreign parcel as absent to prevent courier enumeration. */
@Component
public class ParcelAccessPolicy {
    public void assertCanUpdate(ParcelEntity parcel, AuthenticatedUser user) {
        if (!user.isStaff() && (!user.hasRole("COURIER") || !user.id().equals(parcel.getCourierId()))) {
            throw ResourceNotFoundException.of("Parcel", parcel.getId());
        }
    }
}
