package lk.swiftdrop.user.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * The caller's own profile. The phone is returned in full because it belongs to the caller.
 *
 * @param phone E.164 number, or {@code null} when none is stored
 * @param roles person roles, sorted
 */
public record ProfileResponse(String id, String email, String fullName, String phone, String status,
                              List<String> roles, Instant createdAt, Instant updatedAt) {
}
