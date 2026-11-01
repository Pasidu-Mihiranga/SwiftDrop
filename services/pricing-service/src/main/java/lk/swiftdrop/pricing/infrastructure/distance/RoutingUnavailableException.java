package lk.swiftdrop.pricing.infrastructure.distance;

/** The router could not be asked, as distinct from answering that no route exists. */
class RoutingUnavailableException extends RuntimeException {

    RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    RoutingUnavailableException(String message) {
        super(message);
    }
}
