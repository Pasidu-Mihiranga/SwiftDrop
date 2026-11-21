package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/** Address search is switched off, or the geocoder cannot answer right now. Answered as 503. */
public class AddressSearchUnavailableException extends ApiException {

    public AddressSearchUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "ADDRESS_SEARCH_UNAVAILABLE", message);
    }

    public AddressSearchUnavailableException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
