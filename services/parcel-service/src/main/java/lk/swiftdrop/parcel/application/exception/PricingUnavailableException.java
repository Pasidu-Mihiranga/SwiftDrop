package lk.swiftdrop.parcel.application.exception;

import lk.swiftdrop.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 503: the quote could not be validated because pricing-service timed out, failed, or its circuit
 * is open. Nothing is stored, so the customer can simply try again.
 */
public class PricingUnavailableException extends ApiException {

    public static final String CODE = "PRICING_UNAVAILABLE";

    public PricingUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE,
                "The fee could not be confirmed right now. Nothing was booked. Please try again shortly.");
        initCause(cause);
    }
}
