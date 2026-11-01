package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.application.port.out.DistanceRateRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictCentroidRepository;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Works out how far a same-day job has to travel.
 *
 * <p>Deliberately not inside the pricing transaction. Measuring a lane may mean calling a
 * routing engine over the network, and a blocking call inside a read transaction holds a
 * database connection for its whole duration. On a public endpoint that nobody has to
 * authenticate for, a slow router would then drain the connection pool and take quoting down
 * with it. The coordinates are read in their own short transaction and the call is made
 * outside one.
 */
@Service
public class LaneDistanceService {

    private final DistrictCentroidRepository centroids;
    private final DistanceRateRepository distanceRates;
    private final RoadDistanceProvider roads;

    public LaneDistanceService(DistrictCentroidRepository centroids, DistanceRateRepository distanceRates,
                               RoadDistanceProvider roads) {
        this.centroids = centroids;
        this.distanceRates = distanceRates;
        this.roads = roads;
    }

    /**
     * How far the job is, or empty when it is not a same-day job or cannot be measured.
     *
     * @param origin      the pickup point, or null to fall back to the district's centre
     * @param destination the drop-off point, or null to fall back to the district's centre
     */
    public Optional<LaneDistance> measure(ServiceLevel serviceLevel, String fromDistrict, String toDistrict,
                                          GeoPoint origin, GeoPoint destination, LocalDate quoteDate) {
        if (serviceLevel != ServiceLevel.SAME_DAY) {
            return Optional.empty();
        }
        Optional<GeoPoint> from = origin != null ? Optional.of(origin) : centre(fromDistrict);
        Optional<GeoPoint> to = destination != null ? Optional.of(destination) : centre(toDistrict);
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(roads.between(from.get(), to.get()));
    }

    /** The rate in force, read separately so the pricing transaction need not. */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<DistanceRate> rateInForce(LocalDate on) {
        return distanceRates.findInForce(on);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    protected Optional<GeoPoint> centre(String district) {
        return centroids.findByDistrict(district);
    }
}
