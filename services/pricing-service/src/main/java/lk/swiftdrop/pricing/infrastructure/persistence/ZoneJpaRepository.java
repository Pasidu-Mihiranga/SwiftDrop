package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ZoneJpaRepository extends JpaRepository<ZoneEntity, UUID> {

    /** Districts are fetched eagerly here: every caller renders the coverage with the zone. */
    @EntityGraph(attributePaths = "districts")
    List<ZoneEntity> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "districts")
    List<ZoneEntity> findByActiveIsTrueOrderByCodeAsc();

    @EntityGraph(attributePaths = "districts")
    Optional<ZoneEntity> findByCode(String code);

    boolean existsByCode(String code);
}
