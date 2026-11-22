package lk.swiftdrop.parcel.application.port.out;

/**
 * Business meters for the merchant order API (the M7 Grafana dashboard). Every request that reaches
 * {@link lk.swiftdrop.parcel.application.command.MerchantOrderService#create} is counted once, with
 * how it ended. Requests refused before that (no or malformed Idempotency-Key, an invalid body, a
 * missing token or role) never get here; they show in the HTTP request metrics instead.
 */
public interface MerchantOrderMetrics {

    /** How an order request ended. A closed list, so the number of series stays fixed. */
    enum Outcome {
        /** A new parcel was stored (201). */
        CREATED,
        /** The same Idempotency-Key and body as an earlier order: the original parcel was returned (200). */
        REPLAYED,
        /** The Idempotency-Key was already used for a different order (409). */
        IDEMPOTENCY_CONFLICT,
        /** A concurrent request with the same key had not finished (409, retry). */
        IDEMPOTENCY_IN_PROGRESS,
        /** The merchant is not APPROVED, or parcel-service has not seen it approved (403). */
        NOT_APPROVED,
        /** The token names no merchant (403). */
        NO_MERCHANT,
        /** pricing-service refused the order, e.g. an area it does not serve (422). */
        NOT_PRICEABLE,
        /** pricing-service could not be reached or its circuit is open (503). */
        PRICING_UNAVAILABLE,
        /** Anything else, such as a database failure (5xx). */
        FAILED
    }

    void orderRequest(Outcome outcome);
}
