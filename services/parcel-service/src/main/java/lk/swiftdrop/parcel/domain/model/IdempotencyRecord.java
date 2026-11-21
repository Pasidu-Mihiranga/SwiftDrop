package lk.swiftdrop.parcel.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Proof that a merchant's request with this key created this parcel.
 *
 * @param requestHash SHA-256 (hex) of the request's canonical form; a later request with the same
 *                    key must have the same hash to be a replay
 */
public record IdempotencyRecord(String merchantId, IdempotencyKey key, UUID parcelId, String requestHash,
                                Instant createdAt) {

    public IdempotencyRecord {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
