package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Row of {@code idempotency_record}, keyed by merchant and key (the table's primary key). Read
 * through JPA; written only by {@link SpringDataIdempotencyRecordRepository#insert}, never merged.
 */
@Entity
@Table(name = "idempotency_record")
@IdClass(IdempotencyRecordEntity.Key.class)
public class IdempotencyRecordEntity {

    /** The composite primary key; field names match the two {@code @Id} fields. */
    public static class Key implements Serializable {

        private String merchantId;
        private String idempotencyKey;

        protected Key() {
        }

        public Key(String merchantId, String idempotencyKey) {
            this.merchantId = merchantId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(merchantId, key.merchantId)
                    && Objects.equals(idempotencyKey, key.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(merchantId, idempotencyKey);
        }
    }

    @Id
    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecordEntity() {
    }

    IdempotencyRecord toDomain() {
        return new IdempotencyRecord(merchantId, new IdempotencyKey(idempotencyKey), parcelId, requestHash, createdAt);
    }
}
