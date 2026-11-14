package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: the quote is unknown or older than its 15 minutes. A booking is never made without a valid fee. */
public class QuoteExpiredException extends BusinessRuleViolationException {

    public static final String CODE = "QUOTE_EXPIRED";

    public QuoteExpiredException() {
        super(CODE, "The fee estimate has expired or does not exist. Request a new estimate and confirm again.");
    }
}
