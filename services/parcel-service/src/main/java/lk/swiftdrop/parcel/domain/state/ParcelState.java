package lk.swiftdrop.parcel.domain.state;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;

/** State pattern for the parcel lifecycle. Each concrete state owns its legal successors. */
public interface ParcelState {

    ParcelStatus status();

    /**
     * The state the parcel moves into.
     *
     * @throws InvalidStatusTransitionException when this state does not allow the step
     */
    ParcelState transitionTo(ParcelStatus target);

    default ParcelState reject(ParcelStatus target) {
        throw new InvalidStatusTransitionException(status(), target);
    }
}
