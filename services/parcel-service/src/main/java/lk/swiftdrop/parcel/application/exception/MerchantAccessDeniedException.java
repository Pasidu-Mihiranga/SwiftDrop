package lk.swiftdrop.parcel.application.exception;

import lk.swiftdrop.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 403: the caller may not use the merchant order API. Either its token names no merchant, or the
 * merchant is not APPROVED in parcel-service's status cache (unknown merchants fail closed).
 */
public class MerchantAccessDeniedException extends ApiException {

    public static final String NOT_APPROVED = "MERCHANT_NOT_APPROVED";
    public static final String NO_MERCHANT = "MERCHANT_ID_MISSING";

    private MerchantAccessDeniedException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    /** Pending, rejected, suspended, or not known to parcel-service at all. */
    public static MerchantAccessDeniedException notApproved() {
        return new MerchantAccessDeniedException(NOT_APPROVED, "Only an approved merchant can create orders.");
    }

    public static MerchantAccessDeniedException noMerchant() {
        return new MerchantAccessDeniedException(NO_MERCHANT, "The access token does not name a merchant.");
    }
}
