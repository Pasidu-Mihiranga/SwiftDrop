package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.GeoPoint;

import java.util.Optional;

/**
 * Finds which of the 25 districts a point on the map falls in.
 *
 * <p>The answer comes from district boundaries, not from a geocoder's address text and not from
 * the nearest district centre, both of which are wrong near a border. Empty means the point is
 * outside every district, which is to say outside SwiftDrop's coverage.
 */
public interface DistrictLocator {

    /** The district name exactly as it appears in {@code district_zone}, or empty if outside coverage. */
    Optional<String> districtAt(GeoPoint point);
}
