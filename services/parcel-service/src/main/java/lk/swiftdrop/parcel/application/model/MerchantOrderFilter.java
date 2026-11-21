package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;

/**
 * Filters for a merchant's order list. Every field is optional, and they combine (all must match).
 * The merchant itself is never a filter: it always comes from the caller's token.
 *
 * @param from     orders created at or after this instant
 * @param to       orders created before this instant
 * @param orderRef part of the merchant's own order reference, matched ignoring case. Surrounding
 *                 spaces are dropped, and a blank value means no search.
 */
public record MerchantOrderFilter(ParcelStatus status, Instant from, Instant to, String orderRef) {

    public MerchantOrderFilter {
        orderRef = orderRef == null || orderRef.isBlank() ? null : orderRef.strip();
    }

    /** Without an order-reference search. */
    public MerchantOrderFilter(ParcelStatus status, Instant from, Instant to) {
        this(status, from, to, null);
    }

    public static MerchantOrderFilter none() {
        return new MerchantOrderFilter(null, null, null, null);
    }
}
