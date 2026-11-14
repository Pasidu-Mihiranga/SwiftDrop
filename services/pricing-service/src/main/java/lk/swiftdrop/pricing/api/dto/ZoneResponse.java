package lk.swiftdrop.pricing.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A zone and the districts it serves.
 *
 * @param id omitted from the public coverage response; present for administrators
 */
public record ZoneResponse(UUID id, String code, String name, List<String> districts, boolean active) {
}
