package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.parcel.domain.model.MerchantStanding;

import java.util.Optional;

/**
 * parcel-service's copy of each merchant's status, kept from MerchantLifecycleChanged events
 * (design figure 13). A merchant that is not here has never been seen and is not approved.
 */
public interface MerchantStatusCache {

    Optional<MerchantStanding> find(String merchantId);

    /**
     * The merchant's standing, read inside the caller's order transaction with a shared lock held
     * until it ends. A status change waiting to commit is waited for and seen; one arriving later
     * waits for the order to commit. Concurrent orders do not block each other.
     */
    Optional<MerchantStanding> findForOrder(String merchantId);

    /**
     * Stores {@code incoming} unless a newer change for the merchant is already stored
     * ({@link MerchantStanding#supersededBy}).
     *
     * @return true when the stored standing changed
     */
    boolean apply(MerchantStanding incoming);
}
