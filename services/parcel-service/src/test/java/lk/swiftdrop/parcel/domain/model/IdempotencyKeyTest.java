package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.parcel.domain.exception.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void aUuidOrAnyVisibleAsciiOf8To100CharactersIsAccepted() {
        assertThat(new IdempotencyKey("3f1c2a9e-8b7d-4c6e-9f01-23456789abcd").value()).hasSize(36);
        assertThat(new IdempotencyKey("order:1001/retry#2").value()).isEqualTo("order:1001/retry#2");
        assertThat(new IdempotencyKey("k".repeat(100)).value()).hasSize(100);
        assertThat(new IdempotencyKey("k".repeat(8)).value()).hasSize(8);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "has space", "tab\\there", "ключ", "line\\nbreak"})
    void aMissingBlankOrNonAsciiKeyIsRefused(String key) {
        String value = key == null ? null : key.replace("\\t", "\t").replace("\\n", "\n");
        assertThatThrownBy(() -> new IdempotencyKey(value))
                .isInstanceOfSatisfying(InvalidIdempotencyKeyException.class,
                        e -> assertThat(e.status().value()).isEqualTo(400));
    }

    @Test
    void aKeyShorterThan8CharactersIsRefused() {
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(7))).isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void aKeyLongerThan100CharactersIsRefused() {
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(101))).isInstanceOf(InvalidIdempotencyKeyException.class);
    }
}
