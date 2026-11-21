package lk.swiftdrop.parcel.domain.exception;

import lk.swiftdrop.common.web.error.BadRequestException;

/** 400: the Idempotency-Key header is missing or malformed. */
public class InvalidIdempotencyKeyException extends BadRequestException {

    public static final String CODE = "IDEMPOTENCY_KEY_INVALID";

    public InvalidIdempotencyKeyException() {
        super(CODE, "An Idempotency-Key header of 8 to 100 visible ASCII characters is required.");
    }
}
