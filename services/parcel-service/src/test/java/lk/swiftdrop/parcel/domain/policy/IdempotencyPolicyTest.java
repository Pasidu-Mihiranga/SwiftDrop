package lk.swiftdrop.parcel.domain.policy;

import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;
import lk.swiftdrop.parcel.domain.policy.IdempotencyPolicy.Decision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyPolicyTest {

    private static final String HASH = "a".repeat(64);
    private static final IdempotencyRecord RECORD = new IdempotencyRecord("merchant-1", new IdempotencyKey("idem-key-key-1"),
            UUID.randomUUID(), HASH, Instant.parse("2026-11-20T08:00:00Z"));

    @Test
    void aKeyNotSeenBeforeIsANewOrder() {
        assertThat(IdempotencyPolicy.decide(Optional.empty(), HASH)).isEqualTo(Decision.NEW);
    }

    @Test
    void theSameKeyWithTheSameBodyIsAReplay() {
        assertThat(IdempotencyPolicy.decide(Optional.of(RECORD), HASH)).isEqualTo(Decision.REPLAY);
    }

    @Test
    void theSameKeyWithADifferentBodyIsAConflict() {
        assertThat(IdempotencyPolicy.decide(Optional.of(RECORD), "b".repeat(64))).isEqualTo(Decision.CONFLICT);
    }
}
