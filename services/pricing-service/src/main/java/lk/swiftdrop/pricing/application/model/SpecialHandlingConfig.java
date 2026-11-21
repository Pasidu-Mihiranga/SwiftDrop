package lk.swiftdrop.pricing.application.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/** A handling option as an administrator sees it, including retired ones and display order. */
public record SpecialHandlingConfig(String code, String label, BigDecimal fee, boolean active, int sortOrder) {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");

    public SpecialHandlingConfig {
        Objects.requireNonNull(code, "code");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "A handling code is up to 32 capital letters, digits or underscores, starting with a letter");
        }
        if (label == null || label.isBlank() || label.strip().length() > 100) {
            throw new IllegalArgumentException("A handling option needs a label of up to 100 characters");
        }
        label = label.strip();
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("Handling fee must not be negative");
        }
        if (sortOrder < 0 || sortOrder > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Display order must be between 0 and " + Short.MAX_VALUE);
        }
    }
}
