package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface DistrictCentroidJpaRepository
        extends JpaRepository<DistanceReferenceEntities.Centroid, String> {

    /** District lookup ignores case here as it does everywhere else in this service. */
    @Query("SELECT c FROM DistanceReferenceEntities$Centroid c WHERE lower(c.district) = lower(:district)")
    Optional<DistanceReferenceEntities.Centroid> findByDistrictIgnoringCase(
            @Param("district") String district);
}
