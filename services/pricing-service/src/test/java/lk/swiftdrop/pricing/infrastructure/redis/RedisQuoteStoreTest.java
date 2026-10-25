package lk.swiftdrop.pricing.infrastructure.redis;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisQuoteStoreTest {

    @Test
    void writesTheCompleteSnapshotUsingAnAtomicSetWithExpiry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();
        QuoteSnapshot snapshot = new QuoteSnapshot(id, null, java.util.List.of(), null, null,
                null, null, null, null, null, null, null, null);
        Duration ttl = Duration.ofMinutes(15);
        when(mapper.writeValueAsString(snapshot)).thenReturn("{\"quoteId\":\"" + id + "\"}");

        new RedisQuoteStore(redis, mapper).save(snapshot, ttl);

        verify(values).set("pricing:quote:" + id, "{\"quoteId\":\"" + id + "\"}", ttl);
    }
}
