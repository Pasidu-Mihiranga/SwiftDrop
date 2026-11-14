package lk.swiftdrop.pricing.infrastructure.distance;

import lk.swiftdrop.pricing.application.port.out.DistanceRateRepository;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Straight-line distance scaled by a road factor.
 *
 * <p>A routing engine would give a better answer, and this exists so that not having one never
 * stops a quote. Every result is marked ESTIMATED, so a fee worked out this way can be told
 * from a measured one afterwards rather than quietly passing for the real thing.
 *
 * <p>The scaling factor comes from the distance rate, which is configuration, so it can be
 * calibrated against real journeys without a deploy.
 */
@Component
public class HaversineRoadDistanceProvider implements RoadDistanceProvider {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final DistanceRateRepository distanceRates;
    private final Clock clock;

    public HaversineRoadDistanceProvider(DistanceRateRepository distanceRates, Clock clock) {
        this.distanceRates = distanceRates;
        this.clock = clock;
    }

    @Override
    public LaneDistance between(GeoPoint origin, GeoPoint destination) {
        BigDecimal roadFactor = distanceRates.findInForce(LocalDate.now(clock))
                .map(rate -> rate.roadFactor())
                .orElse(BigDecimal.ONE);
        BigDecimal kilometres = straightLineKm(origin, destination).multiply(roadFactor)
                .setScale(2, RoundingMode.HALF_UP);
        return new LaneDistance(kilometres, DistanceSource.ESTIMATED);
    }

    /** Great-circle distance: the part of this that is not an approximation. */
    public static BigDecimal straightLineKm(GeoPoint origin, GeoPoint destination) {
        double lat1 = Math.toRadians(origin.latitude().doubleValue());
        double lat2 = Math.toRadians(destination.latitude().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(
                destination.longitude().subtract(origin.longitude()).doubleValue());

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
