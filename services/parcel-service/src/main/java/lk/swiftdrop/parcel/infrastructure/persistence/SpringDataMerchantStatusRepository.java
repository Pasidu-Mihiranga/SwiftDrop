package lk.swiftdrop.parcel.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** Spring Data access to {@code merchant_status}. Only {@link MerchantStatusJpaCache} uses it. */
interface SpringDataMerchantStatusRepository extends JpaRepository<MerchantStatusEntity, String> {

    /** @return 1 when the row was inserted, 0 when the merchant already had one */
    @Modifying
    @Query(value = """
            INSERT INTO merchant_status (merchant_id, status, business_name, contact_email, changed_at, updated_at)
            VALUES (:merchantId, :status, :businessName, :contactEmail, :changedAt, :updatedAt)
            ON CONFLICT (merchant_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("merchantId") String merchantId, @Param("status") String status,
                       @Param("businessName") String businessName, @Param("contactEmail") String contactEmail,
                       @Param("changedAt") Instant changedAt, @Param("updatedAt") Instant updatedAt);

    /**
     * The row with a shared lock (FOR SHARE) until the transaction ends: orders do not block each
     * other, but a status update (FOR UPDATE) waits for them, and they wait for it.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from MerchantStatusEntity m where m.merchantId = :merchantId")
    Optional<MerchantStatusEntity> findForOrder(@Param("merchantId") String merchantId);

    /** The row, locked until the transaction ends, so two updates of one merchant run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MerchantStatusEntity m where m.merchantId = :merchantId")
    Optional<MerchantStatusEntity> findForUpdate(@Param("merchantId") String merchantId);
}
