package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * JPA adapter for {@link MerchantStatusCache}.
 *
 * <p>{@link #apply} never lets an older change overwrite a newer one, even when two deliveries
 * race: a merchant's first standing is inserted with {@code ON CONFLICT DO NOTHING}; every later
 * one locks the row and lets {@link MerchantStanding#supersededBy} decide. The ordering rule lives
 * only in the domain.
 */
@Repository
public class MerchantStatusJpaCache implements MerchantStatusCache {

    private final SpringDataMerchantStatusRepository rows;
    private final Clock clock;

    MerchantStatusJpaCache(SpringDataMerchantStatusRepository rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantStanding> find(String merchantId) {
        return rows.findById(merchantId).map(MerchantStatusEntity::toDomain);
    }

    /** Must join the order transaction: the lock is only worth having until that transaction commits. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MerchantStanding> findForOrder(String merchantId) {
        return rows.findForOrder(merchantId).map(MerchantStatusEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean apply(MerchantStanding incoming) {
        int inserted = rows.insertIfAbsent(incoming.merchantId(), incoming.status().name(), incoming.businessName(),
                incoming.contactEmail(), incoming.changedAt(), clock.instant());
        if (inserted == 1) {
            return true;
        }
        MerchantStatusEntity stored = rows.findForUpdate(incoming.merchantId())
                .orElseThrow(() -> new IllegalStateException("merchant_status row vanished while locked"));
        if (!stored.toDomain().supersededBy(incoming)) {
            return false;
        }
        stored.replaceWith(incoming, clock.instant());
        return true;
    }
}
