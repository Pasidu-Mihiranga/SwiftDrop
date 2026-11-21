package lk.swiftdrop.parcel.infrastructure.messaging;

/**
 * A Kafka message that can never be processed: not JSON, or a MerchantLifecycleChanged without a
 * field the cache needs. Retrying cannot help, so the error handler logs it and moves on instead
 * of blocking the partition. It is not marked as processed.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String reason) {
        super(reason);
    }

    public MalformedEventException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
