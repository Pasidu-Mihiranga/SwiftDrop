package lk.swiftdrop.merchant.application.port.out;

import lk.swiftdrop.merchant.application.model.PagedResult;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores merchants. "Active" means any status except REJECTED: a rejected application never blocks
 * a new one.
 */
public interface MerchantRepository {

    /**
     * Inserts a new merchant (version null) or stores a decision on an existing one.
     *
     * @throws lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException when another
     *         active application took the registration number first (a race the pre-check missed)
     * @throws lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException when the owner
     *         already has an active application
     * @throws lk.swiftdrop.common.web.error.ConflictException when someone else changed the merchant first
     */
    Merchant save(Merchant merchant);

    Optional<Merchant> findById(UUID id);

    /** The owner's most recent application, whatever its status. */
    Optional<Merchant> findLatestByOwner(String ownerUserId);

    boolean existsActiveByOwner(String ownerUserId);

    /** @param brNumber already normalised by {@link lk.swiftdrop.merchant.domain.model.BusinessDetails} */
    boolean existsActiveByBrNumber(String brNumber);

    /** One page of applications in one status, oldest first: the order an admin works through the queue. */
    PagedResult<Merchant> findByStatus(MerchantStatus status, int page, int size);
}
