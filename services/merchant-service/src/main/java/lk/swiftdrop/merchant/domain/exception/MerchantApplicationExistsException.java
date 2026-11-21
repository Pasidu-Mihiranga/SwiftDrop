package lk.swiftdrop.merchant.domain.exception;

import lk.swiftdrop.common.web.error.ConflictException;

/**
 * 409: the user already has an application that is pending, approved or suspended. Only after a
 * rejection may the same user apply again.
 */
public class MerchantApplicationExistsException extends ConflictException {

    public static final String CODE = "MERCHANT_APPLICATION_EXISTS";

    public MerchantApplicationExistsException() {
        super(CODE, "You already have a merchant application. You can apply again only if it was rejected.");
    }
}
