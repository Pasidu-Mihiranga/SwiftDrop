package lk.swiftdrop.merchant.application.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: the user who applied no longer has an account, so there is nobody to make a merchant. */
public class MerchantOwnerMissingException extends BusinessRuleViolationException {

    public static final String CODE = "MERCHANT_OWNER_MISSING";

    public MerchantOwnerMissingException() {
        super(CODE, "The account that applied no longer exists, so the application cannot be approved.");
    }
}
