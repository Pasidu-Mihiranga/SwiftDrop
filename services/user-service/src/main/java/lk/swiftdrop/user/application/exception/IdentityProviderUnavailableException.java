package lk.swiftdrop.user.application.exception;

import lk.swiftdrop.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/** 503: Keycloak could not be reached or refused the service account, so the answer would be incomplete. */
public class IdentityProviderUnavailableException extends ApiException {

    public IdentityProviderUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_PROVIDER_UNAVAILABLE", message);
    }
}
