package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.events.common.Types.ParcelStatus;

/** 422: the parcel's lifecycle does not allow this step, for example CREATED to DELIVERED. */
public class InvalidStatusTransitionException extends BusinessRuleViolationException {

    public static final String CODE = "INVALID_STATUS_TRANSITION";

    public InvalidStatusTransitionException(ParcelStatus from, ParcelStatus to) {
        super(CODE, "A parcel cannot move from " + from + " to " + to + ".");
    }

    public InvalidStatusTransitionException(String message) {
        super(CODE, message);
    }
}
