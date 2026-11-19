package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for {@link MerchantRepository}. */
@Repository
public class MerchantJpaRepository implements MerchantRepository {

    /** Partial unique indexes from V2__create_merchant.sql. */
    static final String UNIQUE_ACTIVE_BR_NUMBER = "uq_merchant_br_number_active";
    static final String UNIQUE_ACTIVE_OWNER = "uq_merchant_owner_active";

    private final SpringDataMerchantRepository rows;

    MerchantJpaRepository(SpringDataMerchantRepository rows) {
        this.rows = rows;
    }

    @Override
    public Merchant save(Merchant merchant) {
        MerchantEntity entity = merchant.version() == null
                ? MerchantEntityMapper.toNewEntity(merchant)
                : changed(merchant);
        try {
            // Flush now so a stale version or a duplicate fails here, inside the use case, not at commit.
            return MerchantEntityMapper.toDomain(rows.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException e) {
            throw modifiedElsewhere();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    private MerchantEntity changed(Merchant merchant) {
        MerchantEntity entity = rows.findById(merchant.id()).orElseThrow(MerchantJpaRepository::modifiedElsewhere);
        if (!Objects.equals(entity.getVersion(), merchant.version())) {
            throw modifiedElsewhere();
        }
        MerchantEntityMapper.applyDecision(entity, merchant);
        return entity;
    }

    /**
     * Two identical applications can both pass the existence checks; the unique index lets only one
     * commit. The loser gets the same 409 as if the check had caught it.
     */
    static RuntimeException translate(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains(UNIQUE_ACTIVE_BR_NUMBER)) {
            return new BrNumberAlreadyRegisteredException();
        }
        if (detail.contains(UNIQUE_ACTIVE_OWNER)) {
            return new MerchantApplicationExistsException();
        }
        return e;
    }

    private static ConflictException modifiedElsewhere() {
        return new ConflictException("MERCHANT_MODIFIED",
                "The merchant was changed by someone else. Reload it and try again.");
    }

    @Override
    public Optional<Merchant> findById(UUID id) {
        return rows.findById(id).map(MerchantEntityMapper::toDomain);
    }

    @Override
    public Optional<Merchant> findLatestByOwner(String ownerUserId) {
        return rows.findFirstByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).map(MerchantEntityMapper::toDomain);
    }

    @Override
    public boolean existsActiveByOwner(String ownerUserId) {
        return rows.existsByOwnerUserIdAndStatusNot(ownerUserId, MerchantStatus.REJECTED);
    }

    @Override
    public boolean existsActiveByBrNumber(String brNumber) {
        return rows.existsByBrNumberAndStatusNot(brNumber, MerchantStatus.REJECTED);
    }

    @Override
    public List<Merchant> findByStatus(MerchantStatus status) {
        return rows.findByStatusOrderByCreatedAtAsc(status).stream().map(MerchantEntityMapper::toDomain).toList();
    }
}
