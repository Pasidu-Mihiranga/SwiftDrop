package lk.swiftdrop.pricing.infrastructure.geocoding;

import lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinimumIntervalTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void spacesCallsByTheIntervalAndRefusesOnesThatWouldWaitTooLong() {
        MinimumInterval interval = new MinimumInterval(Duration.ofSeconds(1), Duration.ofMillis(1500), FIXED);

        assertThat(interval.reserve()).isZero();
        assertThat(interval.reserve()).isEqualTo(Duration.ofSeconds(1));
        assertThatThrownBy(interval::reserve)
                .isInstanceOfSatisfying(AddressSearchUnavailableException.class,
                        error -> assertThat(error.getMessage()).contains("busy"));
    }

    @Test
    void doesNotWaitWhenNoIntervalIsSet() {
        MinimumInterval interval = new MinimumInterval(Duration.ZERO, Duration.ZERO, Clock.systemUTC());

        interval.acquire();
        interval.acquire();
        assertThat(interval.reserve()).isZero();
    }

    @Test
    void sleepsUntilItsTurn() {
        MinimumInterval interval = new MinimumInterval(Duration.ofMillis(50), Duration.ofSeconds(1), Clock.systemUTC());
        long start = System.nanoTime();

        interval.acquire();
        interval.acquire();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }
}
