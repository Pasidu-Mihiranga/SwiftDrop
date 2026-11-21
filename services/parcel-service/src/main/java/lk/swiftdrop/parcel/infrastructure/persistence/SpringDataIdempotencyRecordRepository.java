package lk.swiftdrop.parcel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Spring Data access to {@code idempotency_record}. Only {@link IdempotencyJpaStore} uses it. */
interface SpringDataIdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordEntity.Key> {

    /**
     * A plain INSERT. {@code save()} would merge, and merging onto a row a concurrent request has
     * already committed would overwrite it; an INSERT fails on the primary key instead.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_record (merchant_id, idempotency_key, parcel_id, request_hash, created_at)
            VALUES (:merchantId, :idempotencyKey, :parcelId, :requestHash, :createdAt)
            """, nativeQuery = true)
    void insert(@Param("merchantId") String merchantId, @Param("idempotencyKey") String idempotencyKey,
                @Param("parcelId") UUID parcelId, @Param("requestHash") String requestHash,
                @Param("createdAt") Instant createdAt);
}
