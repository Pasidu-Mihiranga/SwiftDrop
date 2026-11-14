package lk.swiftdrop.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One courier for dispatch. The dispatch fields are {@code null} until an admin sets them
 * ({@code profileComplete == false}).
 *
 * @param assignable enabled in Keycloak, details complete and marked active
 */
public record CourierResponse(String id, String fullName, String email, boolean enabled, boolean profileComplete,
                              String vehicleType, UUID homeZoneId, Integer maxDailyCapacity, Boolean active,
                              boolean assignable, Instant profileUpdatedAt) {
}
