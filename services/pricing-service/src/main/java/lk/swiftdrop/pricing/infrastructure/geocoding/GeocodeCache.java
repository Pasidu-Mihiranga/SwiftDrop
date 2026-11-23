package lk.swiftdrop.pricing.infrastructure.geocoding;

import lk.swiftdrop.pricing.application.port.out.AddressGeocoder.Match;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Geocoder answers in Redis. Keys are a SHA-256 of the query, so a cache dump does not list the
 * addresses people searched for. A cache that is down only means the geocoder is asked again.
 */
class GeocodeCache {

    private static final Logger log = LoggerFactory.getLogger(GeocodeCache.class);
    private static final String KEY_PREFIX = "pricing:geocode:";
    private static final TypeReference<List<Cached>> ENTRIES = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    GeocodeCache(StringRedisTemplate redis, ObjectMapper mapper, Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    Optional<List<Match>> find(String query, int limit) {
        try {
            String json = redis.opsForValue().get(key(query, limit));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(json, ENTRIES).stream()
                    .map(entry -> new Match(entry.label(), new GeoPoint(entry.latitude(), entry.longitude())))
                    .toList());
        } catch (DataAccessException | JacksonException | IllegalArgumentException unusable) {
            log.debug("Geocode cache read failed", unusable);
            return Optional.empty();
        }
    }

    void put(String query, int limit, List<Match> matches) {
        try {
            String json = mapper.writeValueAsString(matches.stream()
                    .map(match -> new Cached(match.label(), match.point().latitude(), match.point().longitude()))
                    .toList());
            redis.opsForValue().set(key(query, limit), json, ttl);
        } catch (DataAccessException | JacksonException unusable) {
            log.debug("Geocode cache write failed", unusable);
        }
    }

    static String key(String query, int limit) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((query.strip().toLowerCase(Locale.ROOT) + "|" + limit).getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    record Cached(String label, BigDecimal latitude, BigDecimal longitude) { }
}
