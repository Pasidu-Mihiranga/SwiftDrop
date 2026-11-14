package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: the parcel has already been picked up, delivered or cancelled. */
public class ParcelNotCancellableException extends BusinessRuleViolationException {

    public static final String CODE = "PARCEL_NOT_CANCELLABLE";

    public ParcelNotCancellableException() {
        super(CODE, "Only a parcel that has not been picked up can be cancelled.");
    }
}
