package lk.swiftdrop.parcel.domain.state;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** State pattern for the parcel lifecycle. Each concrete state owns its legal successors. */
public interface ParcelState {
    ParcelStatus status();
    ParcelState transitionTo(ParcelStatus target);

    default ParcelState reject(ParcelStatus target) {
        throw new BusinessRuleViolationException("INVALID_STATUS_TRANSITION",
                "Parcel cannot move from " + status() + " to " + target + ".");
    }
}
