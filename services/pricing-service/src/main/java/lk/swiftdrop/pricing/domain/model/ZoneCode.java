package lk.swiftdrop.pricing.domain.model;

import java.util.regex.Pattern;

/**
 * Stable short code identifying a service zone, for example {@code WP} or {@code NWP}.
 *
 * <p>A zone's code never changes once the zone exists, because rate cards and district
 * assignments are keyed on the zone's identity. Codes are compared case-sensitively after
 * being normalised to upper case.
 */
public record ZoneCode(String value) implements Comparable<ZoneCode> {

    /** Mirrors the pattern published in the OpenAPI contract. */
    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9-]{1,15}$");

    public ZoneCode {
        if (value == null) {
            throw new IllegalArgumentException("Zone code must not be null");
        }
        value = value.strip().toUpperCase();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Zone code must be 2 to 16 upper-case letters, digits or hyphens");
        }
    }

    public static ZoneCode of(String value) {
        return new ZoneCode(value);
    }

    @Override
    public int compareTo(ZoneCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
