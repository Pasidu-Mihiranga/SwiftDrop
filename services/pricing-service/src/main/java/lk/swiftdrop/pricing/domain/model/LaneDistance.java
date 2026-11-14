package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** How far a courier actually has to drive, and how confident we are about it. */
public record LaneDistance(BigDecimal kilometres, DistanceSource source) {

    public LaneDistance {
        Objects.requireNonNull(source, "source");
        if (kilometres == null || kilometres.signum() < 0) {
            throw new IllegalArgumentException("Distance must not be negative");
        }
    }
}
