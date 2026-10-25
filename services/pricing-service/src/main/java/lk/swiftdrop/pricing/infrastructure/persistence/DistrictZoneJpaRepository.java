package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DistrictZoneJpaRepository extends JpaRepository<DistrictZoneEntity, String> {

    List<DistrictZoneEntity> findAllByOrderByDistrictAsc();

    Optional<DistrictZoneEntity> findByDistrictIgnoreCase(String district);
}
