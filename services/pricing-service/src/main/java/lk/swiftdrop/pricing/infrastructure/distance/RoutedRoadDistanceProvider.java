package lk.swiftdrop.pricing.infrastructure.distance;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lk.swiftdrop.pricing.application.port.out.RoadDistanceProvider;
import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Measures a lane along the road network, falling back to an estimate.
 *
 * <p>Points are rounded to three decimal places, about 110 metres, before anything else. The
 * router is then asked about exactly what the cache key says, so a cached distance and a fresh
 * one for the same key cannot differ, and two addresses on one street share an entry.
 *
 * <p>Only a routed distance is cached. An estimate is what the service says when it has no
 * better answer, and remembering it would keep serving that answer for a day after the router
 * came back.
 */
class RoutedRoadDistanceProvider implements RoadDistanceProvider {

    private static final Logger log = LoggerFactory.getLogger(RoutedRoadDistanceProvider.class);
    private static final int COORDINATE_SCALE = 3;

    private final OsrmClient osrm;
    private final RouteCache cache;
    private final CircuitBreaker breaker;
    private final RoadDistanceProvider estimate;

    RoutedRoadDistanceProvider(OsrmClient osrm, RouteCache cache, CircuitBreaker breaker,
                               RoadDistanceProvider estimate) {
        this.osrm = osrm;
        this.cache = cache;
        this.breaker = breaker;
        this.estimate = estimate;
    }

    @Override
    public LaneDistance between(GeoPoint origin, GeoPoint destination) {
        GeoPoint from = rounded(origin);
        GeoPoint to = rounded(destination);
        return cache.find(from, to)
                .or(() -> measure(from, to))
                .map(kilometres -> new LaneDistance(kilometres, DistanceSource.ROUTED))
                .orElseGet(() -> estimate.between(origin, destination));
    }

    private Optional<BigDecimal> measure(GeoPoint from, GeoPoint to) {
        try {
            Optional<BigDecimal> kilometres = breaker.executeSupplier(() -> osrm.drivingKm(from, to));
            kilometres.ifPresent(km -> cache.put(from, to, km));
            return kilometres;
        } catch (RoutingUnavailableException | CallNotPermittedException unavailable) {
            log.debug("Falling back to an estimated distance", unavailable);
            return Optional.empty();
        }
    }

    private static GeoPoint rounded(GeoPoint point) {
        return new GeoPoint(point.latitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                point.longitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
    }
}
