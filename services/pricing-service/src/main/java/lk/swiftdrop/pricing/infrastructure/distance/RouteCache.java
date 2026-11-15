package lk.swiftdrop.pricing.infrastructure.distance;

import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Remembers measured routes, so a popular lane is not re-routed on every estimate.
 *
 * <p>The key keeps its direction, because one-way streets make A to B and B to A different
 * journeys. A cache that cannot be reached is treated as a miss: it only saves work, so it must
 * never be the reason a quote fails.
 */
class RouteCache {

    private static final Logger log = LoggerFactory.getLogger(RouteCache.class);
    private static final String KEY_PREFIX = "pricing:route:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    RouteCache(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    Optional<BigDecimal> find(GeoPoint origin, GeoPoint destination) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(origin, destination))).map(BigDecimal::new);
        } catch (DataAccessException unreachable) {
            log.debug("Route cache read failed", unreachable);
            return Optional.empty();
        }
    }

    void put(GeoPoint origin, GeoPoint destination, BigDecimal kilometres) {
        try {
            redis.opsForValue().set(key(origin, destination), kilometres.toPlainString(), ttl);
        } catch (DataAccessException unreachable) {
            log.debug("Route cache write failed", unreachable);
        }
    }

    private static String key(GeoPoint origin, GeoPoint destination) {
        return KEY_PREFIX + point(origin) + ":" + point(destination);
    }

    private static String point(GeoPoint point) {
        return point.latitude().toPlainString() + "," + point.longitude().toPlainString();
    }
}
