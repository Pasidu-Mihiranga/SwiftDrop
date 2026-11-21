package lk.swiftdrop.merchant.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;

/** 422: the merchant is not in a status that allows this decision, e.g. approving a rejected one. */
public class InvalidMerchantTransitionException extends BusinessRuleViolationException {

    public static final String CODE = "MERCHANT_INVALID_TRANSITION";

    public InvalidMerchantTransitionException(MerchantStatus from, MerchantStatus to) {
        super(CODE, "A merchant in status " + from + " cannot be moved to " + to + ".");
    }
}
