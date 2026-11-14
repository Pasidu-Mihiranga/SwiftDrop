package lk.swiftdrop.parcel.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingNumberFactoryTest {

    /** 20:00 UTC on the 20th is already 01:30 on the 21st in Sri Lanka. */
    private static final Clock LATE_EVENING_UTC = Clock.fixed(Instant.parse("2026-10-20T20:00:00Z"), ZoneOffset.UTC);

    private final TrackingNumberFactory factory = new TrackingNumberFactory(LATE_EVENING_UTC, new Random(42));

    @Test
    void issuesAValidNumberDatedInSriLanka() {
        TrackingNumber number = factory.issue(candidate -> false);

        assertThat(number.value()).startsWith("SD261021");
        assertThat(TrackingNumber.isValid(number.value())).isTrue();
    }

    @Test
    void drawsAgainWhenTheNumberIsAlreadyIssued() {
        List<TrackingNumber> offered = new ArrayList<>();

        TrackingNumber number = factory.issue(candidate -> {
            offered.add(candidate);
            return offered.size() < 3;
        });

        assertThat(offered).hasSize(3).doesNotHaveDuplicates();
        assertThat(number).isEqualTo(offered.get(2));
    }

    @Test
    void givesUpWhenEveryAttemptCollides() {
        List<TrackingNumber> offered = new ArrayList<>();

        assertThatThrownBy(() -> factory.issue(offered::add)).isInstanceOf(IllegalStateException.class);
        assertThat(offered).hasSize(TrackingNumberFactory.MAX_ATTEMPTS);
    }
}
