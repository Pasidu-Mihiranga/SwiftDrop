package lk.swiftdrop.pricing.domain.model;

/**
 * The zone a district belongs to. Every district maps to exactly one zone, which is what makes
 * a quote's origin and destination resolvable from an address.
 */
public record DistrictAssignment(String district, ZoneCode zoneCode, String zoneName) {

    public DistrictAssignment {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("District is required");
        }
        if (zoneCode == null) {
            throw new IllegalArgumentException("Zone code is required");
        }
        district = district.strip();
    }
}
