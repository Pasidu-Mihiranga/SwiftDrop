package lk.swiftdrop.merchant.domain.exception;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;

/** 404: no merchant with that id, or none the caller may see. */
public class MerchantNotFoundException extends ResourceNotFoundException {

    public static final String CODE = "MERCHANT_NOT_FOUND";

    public MerchantNotFoundException() {
        super(CODE, "Merchant not found.");
    }
}
