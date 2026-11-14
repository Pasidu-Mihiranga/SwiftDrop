package lk.swiftdrop.pricing.infrastructure.redis;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These use a real mapper rather than a mock. What matters about this adapter is the bytes it
 * writes and whether it can read back bytes written by a different release, and a mocked mapper
 * can only confirm that some method was called.
 */
class RedisQuoteStoreTest {

    /** Matches the application mapper: unknown fields are ignored. See application.yml. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final Instant ISSUED_AT = Instant.parse("2026-06-01T09:00:00Z");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisQuoteStore store = new RedisQuoteStore(redis, MAPPER);

    private static QuoteSnapshot snapshot(UUID id) {
        return new QuoteSnapshot(id, new QuoteSnapshot.Money(new BigDecimal("609.50"), "LKR"),
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("350.00")),
                        new FeeBreakdown.Line(PricingRule.WEIGHT, new BigDecimal("180.00")),
                        new FeeBreakdown.Line(PricingRule.SIZE, new BigDecimal("79.50"))),
                "WP", "SP", "Colombo", "Matara", new BigDecimal("3"), SizeClassCode.M,
                ServiceLevel.STANDARD, BigDecimal.ZERO, ISSUED_AT, ISSUED_AT.plusSeconds(900));
    }

    @Test
    void writesTheCompleteSnapshotUsingAnAtomicSetWithExpiry() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(15);

        store.save(snapshot(id), ttl);

        var json = forClass(String.class);
        verify(values).set(org.mockito.ArgumentMatchers.eq("pricing:quote:" + id), json.capture(),
                org.mockito.ArgumentMatchers.eq(ttl));
        assertThat(json.getValue()).contains("\"schemaVersion\":1");
        // The fee keeps its scale on the way to Redis: 609.50, never 609.5.
        assertThat(json.getValue()).contains("609.50");
    }

    @Test
    void readsBackEverythingItWrote() {
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        QuoteSnapshot original = snapshot(id);
        store.save(original, Duration.ofMinutes(15));
        var json = forClass(String.class);
        verify(values).set(org.mockito.ArgumentMatchers.anyString(), json.capture(),
                org.mockito.ArgumentMatchers.any(Duration.class));
        when(values.get("pricing:quote:" + id)).thenReturn(json.getValue());

        assertThat(store.findById(id)).contains(original);
    }

    @Test
    void readsAQuoteStoredBeforeTheEnvelopeExisted() {
        // A quote issued by the previous release is still looked up after a deploy. Its value
        // is the bare snapshot, with no schemaVersion wrapper, and it must still resolve.
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        QuoteSnapshot original = snapshot(id);
        when(values.get("pricing:quote:" + id)).thenReturn(MAPPER.writeValueAsString(original));

        assertThat(store.findById(id)).contains(original);
    }

    @Test
    void doesNotDeserializeAnUnknownQuote() {
        when(redis.opsForValue()).thenReturn(values);

        assertThat(store.findById(UUID.randomUUID())).isEqualTo(Optional.empty());
    }
}
