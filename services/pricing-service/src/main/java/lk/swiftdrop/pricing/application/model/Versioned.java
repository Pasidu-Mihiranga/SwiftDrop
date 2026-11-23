package lk.swiftdrop.pricing.application.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A configuration value and the half-open period it applies to, for the admin history views.
 *
 * @param effectiveTo exclusive; null while the value is still open ended
 */
public record Versioned<T>(T value, LocalDate effectiveFrom, LocalDate effectiveTo) {

    public Versioned {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }

    public boolean inForceOn(LocalDate day) {
        return !effectiveFrom.isAfter(day) && (effectiveTo == null || effectiveTo.isAfter(day));
    }
}
