package lk.swiftdrop.parcel.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Spring Data access to {@code delivery_proof}. Only {@link DeliveryProofJpaStore} uses it. */
interface SpringDataDeliveryProofRepository extends JpaRepository<DeliveryProofEntity, UUID> {
}
