package lk.swiftdrop.user.application.model;

import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;

import java.util.UUID;

/**
 * What an admin enters to create a courier, dispatcher or admin account.
 *
 * @param phone   optional
 * @param courier optional dispatch details; only for role COURIER
 */
public record NewStaffAccount(String email, String fullName, PhoneNumber phone, UserRole role, CourierDetails courier) {

    /** A courier's vehicle, home zone and daily capacity, set together. */
    public record CourierDetails(VehicleType vehicleType, UUID homeZoneId, int maxDailyCapacity) {
    }
}
