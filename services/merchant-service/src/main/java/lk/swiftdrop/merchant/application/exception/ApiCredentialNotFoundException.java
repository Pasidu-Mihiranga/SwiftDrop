package lk.swiftdrop.merchant.application.exception;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;

/** 404: the merchant has no API credential in use (none was issued, or it was revoked). */
public class ApiCredentialNotFoundException extends ResourceNotFoundException {

    public static final String CODE = "API_CREDENTIAL_NOT_FOUND";

    public ApiCredentialNotFoundException() {
        super(CODE, "This merchant has no API credential in use.");
    }
}
