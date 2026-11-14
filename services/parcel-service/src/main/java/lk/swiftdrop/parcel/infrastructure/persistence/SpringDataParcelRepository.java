package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data access to {@code parcel}. Only {@link ParcelJpaRepository} uses it. */
interface SpringDataParcelRepository extends JpaRepository<ParcelEntity, UUID> {

    boolean existsByTrackingNumber(String trackingNumber);

    Page<ParcelEntity> findByCustomerId(String customerId, Pageable pageable);

    Page<ParcelEntity> findByCustomerIdAndStatus(String customerId, ParcelStatus status, Pageable pageable);
}
