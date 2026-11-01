package lk.swiftdrop.pricing.infrastructure.redis;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * A single atomic Redis SET stores the complete snapshot with its expiry.
 *
 * <p>The value is written inside a small envelope that carries a schema version:
 *
 * <pre>
 *   {"schemaVersion":1,"quote":{ ... }}
 * </pre>
 *
 * <p>The version exists because quotes outlive a deploy. A snapshot issued fifteen minutes
 * before a release is still looked up afterwards, by a replica running the new code, so the
 * reader has to cope with a payload the writer never produced. A value with no version is
 * therefore read as the original bare snapshot rather than rejected, and unknown fields are
 * ignored (see {@code spring.jackson.deserialization} in application.yml) so a newer writer
 * during a rolling update does not break an older reader.
 *
 * <p>Both directions bind straight to a record rather than going through a JsonNode tree. That
 * is deliberate: the node tree strips trailing zeros from a BigDecimal, which would turn a fee
 * of 609.50 into 609.5 and lose the scale the whole money model is built on.
 */
@Repository
public class RedisQuoteStore implements QuoteStore {

    private static final String KEY_PREFIX = "pricing:quote:";

    /** Bumped only when the stored shape changes in a way a reader has to branch on. */
    static final int CURRENT_SCHEMA_VERSION = 1;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisQuoteStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        // The application mapper is used as-is, so a stored quote and an API response always
        // agree on how a date or a decimal is written.
        this.mapper = mapper;
    }

    @Override
    public void save(QuoteSnapshot quote, Duration ttl) {
        String json = mapper.writeValueAsString(new StoredQuote(CURRENT_SCHEMA_VERSION, quote));
        redis.opsForValue().set(KEY_PREFIX + quote.quoteId(), json, ttl);
    }

    @Override
    public Optional<QuoteSnapshot> findById(UUID quoteId) {
        String json = redis.opsForValue().get(KEY_PREFIX + quoteId);
        return Optional.ofNullable(json).map(this::read);
    }

    private QuoteSnapshot read(String json) {
        StoredQuote stored = mapper.readValue(json, StoredQuote.class);
        // No version means the value predates the envelope: the whole document is the snapshot.
        return stored.schemaVersion() == null
                ? mapper.readValue(json, QuoteSnapshot.class)
                : stored.quote();
    }

    /**
     * The stored envelope.
     *
     * @param schemaVersion null when read from a value written before the envelope existed
     */
    record StoredQuote(Integer schemaVersion, QuoteSnapshot quote) { }
}
