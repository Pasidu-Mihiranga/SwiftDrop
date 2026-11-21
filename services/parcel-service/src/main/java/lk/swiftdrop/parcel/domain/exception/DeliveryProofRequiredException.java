package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;

/** 422: a delivery needs proof: the recipient's name, or an uploaded photo or signature. */
public class DeliveryProofRequiredException extends BusinessRuleViolationException {

    public static final String CODE = "DELIVERY_PROOF_REQUIRED";

    public DeliveryProofRequiredException(String message) {
        super(CODE, message);
    }
}
