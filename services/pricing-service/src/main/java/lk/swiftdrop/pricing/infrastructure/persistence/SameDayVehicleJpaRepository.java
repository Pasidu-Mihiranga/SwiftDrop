package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SameDayVehicleJpaRepository extends JpaRepository<PricingExtensionEntities.Vehicle, String> {

    List<PricingExtensionEntities.Vehicle> findByActiveTrueOrderByCodeAsc();

    Optional<PricingExtensionEntities.Vehicle> findByCodeAndActiveTrue(String code);

    List<PricingExtensionEntities.Vehicle> findAllByOrderByCodeAsc();
}
