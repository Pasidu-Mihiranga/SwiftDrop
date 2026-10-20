package lk.swiftdrop.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for business exceptions that map to an HTTP status. The message is returned to
 * the client as the problem "detail", so it must never contain personal data.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** HTTP status sent to the client. */
    public HttpStatus status() {
        return status;
    }

    /** Stable, machine-readable error code such as {@code PARCEL_NOT_FOUND}. */
    public String code() {
        return code;
    }
}
