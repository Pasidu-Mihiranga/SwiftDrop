package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.events.common.Types.ParcelStatus;

import java.time.Instant;

/**
 * Filters for a merchant's order list. Every field is optional.
 *
 * @param from orders created at or after this instant
 * @param to   orders created before this instant
 */
public record MerchantOrderFilter(ParcelStatus status, Instant from, Instant to) {

    public static MerchantOrderFilter none() {
        return new MerchantOrderFilter(null, null, null);
    }
}
