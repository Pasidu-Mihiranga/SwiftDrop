package lk.swiftdrop.pricing.application.model;

import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.VehicleType;

import java.util.Objects;

/** A same-day vehicle as an administrator sees it: its limits, label and whether it is offered. */
public record SameDayVehicleConfig(SameDayVehicle limits, String label, boolean active) {

    public SameDayVehicleConfig {
        Objects.requireNonNull(limits, "limits");
        if (label == null || label.isBlank() || label.strip().length() > 60) {
            throw new IllegalArgumentException("A vehicle needs a label of up to 60 characters");
        }
        label = label.strip();
    }

    public VehicleType type() {
        return limits.type();
    }
}
