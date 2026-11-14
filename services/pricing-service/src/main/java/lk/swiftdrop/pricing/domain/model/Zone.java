package lk.swiftdrop.pricing.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * A service zone together with the districts currently mapped to it.
 *
 * <p>Only active zones can be quoted. The district list is the coverage the public API
 * advertises, so it is always sorted and immutable.
 *
 * @param id       surrogate key; {@code null} before the zone has been persisted
 * @param code     stable business key
 * @param name     display name, for example "North Western"
 * @param active   whether the zone may be used for new quotes
 * @param districts districts mapped to this zone, sorted by name
 */
public record Zone(UUID id, ZoneCode code, String name, boolean active, List<String> districts) {

    public Zone {
        if (code == null) {
            throw new IllegalArgumentException("Zone code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Zone name is required");
        }
        name = name.strip();
        districts = districts == null ? List.of() : districts.stream().sorted().toList();
    }

    /** A zone that has not been persisted yet and has no districts assigned. */
    public static Zone newZone(ZoneCode code, String name, boolean active) {
        return new Zone(null, code, name, active, List.of());
    }

    /** Applies the only two mutable attributes, keeping identity and coverage intact. */
    public Zone with(String newName, boolean newActive) {
        return new Zone(id, code, newName, newActive, districts);
    }

    public boolean hasDistricts() {
        return !districts.isEmpty();
    }
}
