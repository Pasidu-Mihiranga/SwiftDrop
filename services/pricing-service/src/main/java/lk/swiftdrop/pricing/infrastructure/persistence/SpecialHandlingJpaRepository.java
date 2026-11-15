package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpecialHandlingJpaRepository extends JpaRepository<PricingExtensionEntities.Handling, String> {

    List<PricingExtensionEntities.Handling> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
