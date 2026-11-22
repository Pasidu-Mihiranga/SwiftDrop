package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.parcel.application.port.out.DuplicateIdempotencyKeyException;
import lk.swiftdrop.parcel.application.port.out.IdempotencyStore;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter for {@link IdempotencyStore}. The table's primary key (merchant_id, idempotency_key)
 * is what makes one key create at most one parcel: a second insert, even from a request running
 * at the same moment, waits for the first and then fails on it.
 */
@Repository
public class IdempotencyJpaStore implements IdempotencyStore {

    static final String PRIMARY_KEY = "idempotency_record_pkey";

    private final SpringDataIdempotencyRecordRepository rows;

    IdempotencyJpaStore(SpringDataIdempotencyRecordRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(String merchantId, IdempotencyKey key) {
        return rows.findById(new IdempotencyRecordEntity.Key(merchantId, key.value()))
                .map(IdempotencyRecordEntity::toDomain);
    }

    /** Joins the order transaction, so a failure here rolls back the parcel and its outbox row too. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(IdempotencyRecord record) {
        try {
            rows.insert(record.merchantId(), record.key().value(), record.parcelId(), record.requestHash(),
                    record.createdAt());
        } catch (DataIntegrityViolationException e) {
            if (isPrimaryKeyViolation(e)) {
                throw new DuplicateIdempotencyKeyException(e);
            }
            throw e;
        }
    }

    /** Only the merchant + key collision is a duplicate; a missing parcel (foreign key) is a real error. */
    private static boolean isPrimaryKeyViolation(DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause().getMessage();
        return detail != null && detail.contains(PRIMARY_KEY);
    }
}
