package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;

import java.util.Optional;

/** The merchant order API's idempotency records, one per merchant and key. */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String merchantId, IdempotencyKey key);

    /**
     * Inserts the record in the caller's transaction.
     *
     * @throws DuplicateIdempotencyKeyException when a concurrent request stored the same merchant
     *         and key first; the caller's transaction must then roll back
     */
    void save(IdempotencyRecord record);
}
