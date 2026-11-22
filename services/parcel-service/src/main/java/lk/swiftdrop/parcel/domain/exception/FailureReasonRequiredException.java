package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: a failed delivery needs a reason, so the customer and dispatch know what happened. */
public class FailureReasonRequiredException extends BusinessRuleViolationException {

    public static final String CODE = "FAILURE_REASON_REQUIRED";

    public FailureReasonRequiredException() {
        super(CODE, "Choose why the delivery failed.");
    }
}
