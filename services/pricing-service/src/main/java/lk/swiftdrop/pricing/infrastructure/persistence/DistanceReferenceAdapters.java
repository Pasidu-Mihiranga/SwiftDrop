package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.DistanceRateRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictCentroidRepository;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JPA adapters for the distance reference tables. */
@Repository
class DistanceReferenceAdapters implements DistrictCentroidRepository, DistanceRateRepository {

    private final DistrictCentroidJpaRepository centroids;
    private final DistanceRateJpaRepository rates;

    DistanceReferenceAdapters(DistrictCentroidJpaRepository centroids, DistanceRateJpaRepository rates) {
        this.centroids = centroids;
        this.rates = rates;
    }

    @Override
    public Optional<GeoPoint> findByDistrict(String district) {
        if (district == null || district.isBlank()) {
            return Optional.empty();
        }
        return centroids.findByDistrictIgnoringCase(district.strip())
                .map(centroid -> new GeoPoint(centroid.getLatitude(), centroid.getLongitude()));
    }

    @Override
    public Optional<DistanceRate> findInForce(LocalDate on) {
        List<DistanceReferenceEntities.Rate> inForce = rates.findInForce(on);
        // A unique constraint on effective_from means at most one rate can start on a given
        // day, so the newest in force is unambiguous.
        if (inForce.isEmpty()) {
            return Optional.empty();
        }
        DistanceReferenceEntities.Rate rate = inForce.get(0);
        return Optional.of(new DistanceRate(rate.getFlagFall(), rate.getPerKmFee(),
                rate.getMinChargeableKm(), rate.getRoadFactor()));
    }
}
