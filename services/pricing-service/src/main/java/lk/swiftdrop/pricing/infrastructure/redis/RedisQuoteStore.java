package lk.swiftdrop.pricing.infrastructure.redis;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** A single atomic Redis SET stores the complete snapshot with its expiry. */
@Repository
public class RedisQuoteStore implements QuoteStore {

    private static final String KEY_PREFIX = "pricing:quote:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisQuoteStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void save(QuoteSnapshot quote, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + quote.quoteId(), mapper.writeValueAsString(quote), ttl);
    }

    @Override
    public Optional<QuoteSnapshot> findById(UUID quoteId) {
        String json = redis.opsForValue().get(KEY_PREFIX + quoteId);
        return Optional.ofNullable(json).map(value -> mapper.readValue(value, QuoteSnapshot.class));
    }
}
