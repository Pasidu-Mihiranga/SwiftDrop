package lk.swiftdrop.pricing.infrastructure.geocoding;

import lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Spaces calls at least {@code interval} apart within this instance, waiting up to
 * {@code maxWait} for a turn. Public Nominatim allows one request a second per application; a
 * deployment with several replicas should use its own geocoder or divide the interval.
 */
class MinimumInterval {

    private final Duration interval;
    private final Duration maxWait;
    private final Clock clock;
    private Instant nextAllowed = Instant.MIN;

    MinimumInterval(Duration interval, Duration maxWait, Clock clock) {
        this.interval = interval;
        this.maxWait = maxWait;
        this.clock = clock;
    }

    /** Returns when this call may go; refuses it if that would mean waiting longer than allowed. */
    void acquire() {
        Duration wait = reserve();
        if (wait.isPositive()) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AddressSearchUnavailableException("Address search was interrupted.", interrupted);
            }
        }
    }

    /** Claims the next slot and says how long to wait for it. */
    synchronized Duration reserve() {
        Instant now = clock.instant();
        Instant slot = nextAllowed.isAfter(now) ? nextAllowed : now;
        Duration wait = Duration.between(now, slot);
        if (wait.compareTo(maxWait) > 0) {
            throw new AddressSearchUnavailableException("Address search is busy. Try again in a moment, or pick the"
                    + " point on the map.");
        }
        nextAllowed = slot.plus(interval);
        return wait;
    }
}
