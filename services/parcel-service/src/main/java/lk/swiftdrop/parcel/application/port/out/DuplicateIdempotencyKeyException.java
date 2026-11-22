package lk.swiftdrop.parcel.application.port.out;

/**
 * Another request with the same merchant and Idempotency-Key committed first. Raised inside the
 * order transaction so that the parcel and outbox row written with it are rolled back too.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(Throwable cause) {
        super("The idempotency key was stored by a concurrent request", cause);
    }
}
