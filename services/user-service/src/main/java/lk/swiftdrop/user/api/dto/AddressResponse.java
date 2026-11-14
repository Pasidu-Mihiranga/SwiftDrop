package lk.swiftdrop.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/** One saved address of the caller, decrypted for the owner only. */
public record AddressResponse(UUID id, String label, String line1, String line2, String city, String district,
                              String postalCode, Double lat, Double lng, boolean isDefault, Instant createdAt,
                              Instant updatedAt) {
}
