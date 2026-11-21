package lk.swiftdrop.parcel.application.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/**
 * 422: pricing-service refused to price the order (for example DISTRICT_NOT_SERVICED or
 * SIZE_CLASS_WEIGHT_EXCEEDED). pricing-service's own code is passed on so the merchant can fix the
 * order; nothing was stored, so the same Idempotency-Key may be reused with a corrected body.
 */
public class OrderNotPriceableException extends BusinessRuleViolationException {

    public static final String DEFAULT_CODE = "ORDER_NOT_PRICEABLE";

    public OrderNotPriceableException(String pricingCode, String detail) {
        super(pricingCode == null || pricingCode.isBlank() ? DEFAULT_CODE : pricingCode,
                detail == null || detail.isBlank() ? "This order cannot be priced." : detail);
    }
}
