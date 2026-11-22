package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** An extra a customer can ask for, such as fragile handling, and its flat fee. */
public record SpecialHandlingOption(String code, String label, BigDecimal fee) {

    public SpecialHandlingOption {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("Handling fee must not be negative");
        }
    }
}
