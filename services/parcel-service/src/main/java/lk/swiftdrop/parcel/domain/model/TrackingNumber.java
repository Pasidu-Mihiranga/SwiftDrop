package lk.swiftdrop.parcel.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Public identifier of a parcel: {@code SD} + booking date ({@code YYMMDD}) + six base 32
 * characters + one check character, for example {@code SD261020K7Q2M9X}.
 *
 * <p>The check character (Luhn mod 32) catches a mistyped or swapped character before a lookup
 * is made. The alphabet is Crockford base 32, which leaves out I, L, O and U because they are
 * easily misread.
 */
public record TrackingNumber(String value) {

    public static final int BODY_LENGTH = 6;
    static final String PREFIX = "SD";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Pattern FORMAT =
            Pattern.compile(PREFIX + "\\d{6}[" + ALPHABET + "]{" + (BODY_LENGTH + 1) + "}");

    public TrackingNumber {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Malformed tracking number");
        }
    }

    /** Builds the number for a booking date and a random body, adding the check character. */
    public static TrackingNumber of(LocalDate bookingDate, String body) {
        String payload = DATE.format(bookingDate) + body;
        return new TrackingNumber(PREFIX + payload + checkCharacter(payload));
    }

    public static boolean isValid(String candidate) {
        if (candidate == null || !FORMAT.matcher(candidate).matches()) {
            return false;
        }
        String payload = candidate.substring(PREFIX.length(), candidate.length() - 1);
        return candidate.charAt(candidate.length() - 1) == checkCharacter(payload);
    }

    /** Luhn mod N over the alphabet, so the check character is itself a base 32 character. */
    static char checkCharacter(String payload) {
        int base = ALPHABET.length();
        int factor = 2;
        int sum = 0;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int addend = factor * ALPHABET.indexOf(payload.charAt(i));
            factor = factor == 2 ? 1 : 2;
            sum += addend / base + addend % base;
        }
        return ALPHABET.charAt((base - sum % base) % base);
    }

    @Override
    public String toString() {
        return value;
    }
}
