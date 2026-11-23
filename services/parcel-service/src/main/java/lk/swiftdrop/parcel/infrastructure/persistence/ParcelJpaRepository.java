package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for {@link ParcelRepository}. */
@Repository
public class ParcelJpaRepository implements ParcelRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    /** The id breaks ties so a page boundary never repeats or skips an order. */
    private static final Sort NEWEST_FIRST_STABLE = NEWEST_FIRST.and(Sort.by(Sort.Direction.DESC, "id"));
    /** The escape character for LIKE patterns built from a merchant's search text. */
    private static final char LIKE_ESCAPE = '\\';

    private final SpringDataParcelRepository rows;

    ParcelJpaRepository(SpringDataParcelRepository rows) {
        this.rows = rows;
    }

    @Override
    public Parcel save(Parcel parcel) {
        ParcelEntity entity = parcel.version() == null
                ? ParcelEntityMapper.toNewEntity(parcel)
                : changed(parcel);
        try {
            // Flush now so a stale version fails here, inside the use case, not at commit.
            return ParcelEntityMapper.toDomain(rows.saveAndFlush(entity), true);
        } catch (OptimisticLockingFailureException e) {
            throw modifiedElsewhere();
        }
    }

    private ParcelEntity changed(Parcel parcel) {
        ParcelEntity entity = rows.findById(parcel.id()).orElseThrow(ParcelJpaRepository::modifiedElsewhere);
        if (!Objects.equals(entity.getVersion(), parcel.version())) {
            throw modifiedElsewhere();
        }
        ParcelEntityMapper.applyChanges(entity, parcel);
        return entity;
    }

    private static ConflictException modifiedElsewhere() {
        return new ConflictException("PARCEL_MODIFIED", "The parcel was changed by someone else. Reload it and try again.");
    }

    @Override
    public Optional<Parcel> findById(UUID id) {
        return rows.findById(id).map(entity -> ParcelEntityMapper.toDomain(entity, true));
    }

    @Override
    public boolean existsByTrackingNumber(TrackingNumber trackingNumber) {
        return rows.existsByTrackingNumber(trackingNumber.value());
    }

    @Override
    public PagedResult<Parcel> findByCustomer(String customerId, ParcelStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<ParcelEntity> found = status == null
                ? rows.findByCustomerId(customerId, pageable)
                : rows.findByCustomerIdAndStatus(customerId, status, pageable);
        return new PagedResult<>(found.map(entity -> ParcelEntityMapper.toDomain(entity, false)).getContent(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @Override
    public PagedResult<Parcel> findByCourier(String courierId, ParcelStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<ParcelEntity> found = status == null
                ? rows.findByCourierId(courierId, pageable)
                : rows.findByCourierIdAndStatus(courierId, status, pageable);
        return new PagedResult<>(found.map(entity -> ParcelEntityMapper.toDomain(entity, false)).getContent(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    @Override
    public PagedResult<Parcel> findByMerchant(String merchantId, MerchantOrderFilter filter, int page, int size) {
        Objects.requireNonNull(merchantId, "merchantId");
        Specification<ParcelEntity> query = (root, q, cb) -> cb.equal(root.get("merchantId"), merchantId);
        if (filter.status() != null) {
            query = query.and((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.from() != null) {
            query = query.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
        }
        if (filter.to() != null) {
            query = query.and((root, q, cb) -> cb.lessThan(root.<Instant>get("createdAt"), filter.to()));
        }
        if (filter.orderRef() != null) {
            // A bound parameter, with LIKE's wildcards escaped: "%" or "_" typed by the merchant are
            // matched literally, never as "anything". The merchant condition above always applies.
            String pattern = "%" + escapeLike(filter.orderRef().toLowerCase(Locale.ROOT)) + "%";
            query = query.and((root, q, cb) ->
                    cb.like(cb.lower(root.get("merchantOrderRef")), pattern, LIKE_ESCAPE));
        }
        Page<ParcelEntity> found = rows.findAll(query, PageRequest.of(page, size, NEWEST_FIRST_STABLE));
        return new PagedResult<>(found.map(entity -> ParcelEntityMapper.toDomain(entity, false)).getContent(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    /** Makes LIKE's wildcards and the escape character itself literal. */
    static String escapeLike(String text) {
        return text.replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
