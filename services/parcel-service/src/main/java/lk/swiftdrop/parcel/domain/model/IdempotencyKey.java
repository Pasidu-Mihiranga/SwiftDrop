package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.parcel.domain.exception.InvalidIdempotencyKeyException;

import java.util.regex.Pattern;

/**
 * The merchant's {@code Idempotency-Key} header: 8 to 100 visible ASCII characters, as the shared
 * contract defines it (contracts/openapi/common.v1.yaml); a UUID is typical. Keys are scoped to one merchant, so two merchants may use the same key.
 */
public record IdempotencyKey(String value) {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;
    private static final Pattern VISIBLE_ASCII =
            Pattern.compile("^[\\x21-\\x7E]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

    /** @throws InvalidIdempotencyKeyException when the header is missing, blank, shorter than 8 or longer than 100 characters, or not visible ASCII */
    public IdempotencyKey {
        if (value == null || !VISIBLE_ASCII.matcher(value).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
