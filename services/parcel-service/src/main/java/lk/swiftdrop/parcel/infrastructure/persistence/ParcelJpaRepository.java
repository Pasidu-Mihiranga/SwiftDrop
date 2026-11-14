package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for {@link ParcelRepository}. */
@Repository
public class ParcelJpaRepository implements ParcelRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

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
}
