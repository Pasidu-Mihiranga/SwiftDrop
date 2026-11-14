package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SizeClassJpaRepository extends JpaRepository<SizeClassEntity, String> {

    List<SizeClassEntity> findAllByOrderByMultiplierAsc();
}
