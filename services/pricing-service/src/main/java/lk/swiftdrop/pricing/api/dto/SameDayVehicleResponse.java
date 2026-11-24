package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.domain.model.VehicleType;

import java.math.BigDecimal;

/** A same-day vehicle, its limits and whether it is offered. */
public record SameDayVehicleResponse(VehicleType code, String label, BigDecimal maxWeightKg,
                                     BigDecimal maxDistanceKm, BigDecimal rateMultiplier, boolean active) {

    public static SameDayVehicleResponse of(SameDayVehicleConfig vehicle) {
        return new SameDayVehicleResponse(vehicle.type(), vehicle.label(), vehicle.limits().maxWeightKg(),
                vehicle.limits().maxDistanceKm(), vehicle.limits().rateMultiplier(), vehicle.active());
    }
}
