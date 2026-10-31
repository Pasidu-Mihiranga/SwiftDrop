package lk.swiftdrop.reporting.infrastructure.messaging;

/** A record that can never be applied, however often it is retried: bad JSON, wrong type or unknown version. */
class MalformedEventException extends RuntimeException {

    MalformedEventException(String message) {
        super(message);
    }

    MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
