package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: the quote was priced for another route, so its fee does not apply to this booking. */
public class QuoteMismatchException extends BusinessRuleViolationException {

    public static final String CODE = "QUOTE_MISMATCH";

    public QuoteMismatchException() {
        super(CODE, "The fee estimate was issued for a different route. Request a new estimate for these addresses.");
    }
}
