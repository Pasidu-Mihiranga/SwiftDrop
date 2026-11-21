package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.ConflictException;

/** 409: this merchant already used the Idempotency-Key for a different order. Nothing was created. */
public class IdempotencyKeyReusedException extends ConflictException {

    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";

    public IdempotencyKeyReusedException() {
        super(CODE, "This Idempotency-Key was already used for a different order. Use a new key for a new order.");
    }
}
