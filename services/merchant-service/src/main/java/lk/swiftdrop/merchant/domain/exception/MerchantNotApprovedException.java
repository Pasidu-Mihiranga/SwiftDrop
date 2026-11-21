package lk.swiftdrop.merchant.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;

/** 422: only an APPROVED merchant may use its API credentials, e.g. not a suspended one. */
public class MerchantNotApprovedException extends BusinessRuleViolationException {

    public static final String CODE = "MERCHANT_NOT_APPROVED";

    public MerchantNotApprovedException(MerchantStatus status) {
        super(CODE, "Only an approved merchant can manage API credentials; this merchant is " + status + ".");
    }
}
