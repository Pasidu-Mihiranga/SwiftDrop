package lk.swiftdrop.merchant.application.exception;

import lk.swiftdrop.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 503: Keycloak could not be reached or refused the merchant-service service account. Nothing was
 * committed, and every provisioning call is safe to repeat, so the admin can simply try again.
 */
public class IdentityProviderUnavailableException extends ApiException {

    public static final String CODE = "IDENTITY_PROVIDER_UNAVAILABLE";

    public IdentityProviderUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, message);
    }
}
