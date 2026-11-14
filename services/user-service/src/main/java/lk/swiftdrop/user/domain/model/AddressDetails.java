package lk.swiftdrop.user.domain.model;

import java.util.regex.Pattern;

/**
 * What a person types for one saved address (the shape of {@code common.v1.yaml Address} plus a label).
 *
 * @param label      the person's own name for it, e.g. "Home"
 * @param line1      street line, personal data (encrypted at rest)
 * @param line2      optional second line, personal data (encrypted at rest)
 * @param city       e.g. "Kandy"
 * @param district   one of the 25 districts (pricing-service owns the list)
 * @param postalCode five digits, optional
 * @param lat        optional; given together with {@code lng}
 * @param lng        optional; given together with {@code lat}
 */
public record AddressDetails(String label, String line1, String line2, String city, String district,
                             String postalCode, Double lat, Double lng) {

    private static final Pattern POSTAL_CODE = Pattern.compile("^[0-9]{5}$");

    public AddressDetails {
        label = required(label, "Label", 40);
        line1 = required(line1, "Address line 1", 120);
        line2 = optional(line2, "Address line 2", 120);
        city = required(city, "City", 60);
        district = required(district, "District", 40);
        postalCode = optional(postalCode, "Postal code", 5);
        if (postalCode != null && !POSTAL_CODE.matcher(postalCode).matches()) {
            throw new IllegalArgumentException("Postal code must be five digits");
        }
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be given together");
        }
        if (lat != null && (lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
            throw new IllegalArgumentException("Coordinates are out of range");
        }
    }

    private static String required(String value, String field, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(field + " must be 1 to " + max + " characters");
        }
        return stripped;
    }

    private static String optional(String value, String field, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return stripped.isEmpty() ? null : stripped;
    }

    /** Never print street lines: toString ends up in logs. */
    @Override
    public String toString() {
        return "AddressDetails[label=" + label + ", city=" + city + ", district=" + district + "]";
    }
}
