package lk.swiftdrop.parcel.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Issues tracking numbers. A number that is already in use is thrown away and another is drawn;
 * the unique constraint on the table remains the final guard.
 */
public class TrackingNumberFactory {

    /** Six base 32 characters give about a billion numbers a day, so a retry is already rare. */
    static final int MAX_ATTEMPTS = 5;
    /** The date in a tracking number is the business date in Sri Lanka, not the UTC date. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final Clock clock;
    private final RandomGenerator random;

    public TrackingNumberFactory(Clock clock, RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    /**
     * @param alreadyIssued answers whether a candidate is taken
     * @throws IllegalStateException when every attempt collided, which signals a fault rather than bad luck
     */
    public TrackingNumber issue(Predicate<TrackingNumber> alreadyIssued) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            TrackingNumber candidate = next();
            if (!alreadyIssued.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No unused tracking number after " + MAX_ATTEMPTS + " attempts");
    }

    private TrackingNumber next() {
        StringBuilder body = new StringBuilder(TrackingNumber.BODY_LENGTH);
        for (int i = 0; i < TrackingNumber.BODY_LENGTH; i++) {
            body.append(TrackingNumber.ALPHABET.charAt(random.nextInt(TrackingNumber.ALPHABET.length())));
        }
        return TrackingNumber.of(LocalDate.now(clock.withZone(BUSINESS_ZONE)), body.toString());
    }
}
