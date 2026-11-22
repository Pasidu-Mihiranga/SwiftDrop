package lk.swiftdrop.parcel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataDeliveryProofRepository extends JpaRepository<DeliveryProofEntity, UUID> {
    Optional<DeliveryProofEntity> findByIdAndParcelId(UUID id, UUID parcelId);
}
