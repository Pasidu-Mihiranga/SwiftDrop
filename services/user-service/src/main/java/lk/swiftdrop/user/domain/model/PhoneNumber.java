package lk.swiftdrop.user.domain.model;

import java.util.regex.Pattern;

/**
 * A Sri Lankan mobile number, always held in E.164 form ({@code +947XXXXXXXX}).
 *
 * <p>Users may type either the local form ({@code 0771234567}) or the international form
 * ({@code +94771234567}); both normalise to the same value, so the same phone is never stored twice
 * in two shapes.
 *
 * @param value E.164 number, for example {@code +94771234567}
 */
public record PhoneNumber(String value) {

    private static final Pattern E164 = Pattern.compile("^\\+947[0-9]{8}$");
    private static final Pattern INPUT = Pattern.compile("^(\\+94|0)7[0-9]{8}$");

    public PhoneNumber {
        if (value == null || !E164.matcher(value).matches()) {
            throw new IllegalArgumentException("Phone number must be a Sri Lankan mobile number in E.164 form");
        }
    }

    /** Accepts {@code 07XXXXXXXX} or {@code +947XXXXXXXX}, ignoring surrounding spaces. */
    public static PhoneNumber parse(String input) {
        String trimmed = input == null ? "" : input.strip();
        if (!INPUT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Phone number must look like 0771234567 or +94771234567");
        }
        return new PhoneNumber(trimmed.startsWith("0") ? "+94" + trimmed.substring(1) : trimmed);
    }

    /** Never print the number itself: toString ends up in logs. */
    @Override
    public String toString() {
        return "PhoneNumber[***" + value.substring(value.length() - 2) + "]";
    }
}
