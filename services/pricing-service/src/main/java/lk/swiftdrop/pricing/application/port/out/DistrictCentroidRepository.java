package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.GeoPoint;

import java.util.Optional;

/**
 * Roughly where a district is.
 *
 * <p>Used only when a same-day caller gives no precise pickup or drop-off point. A centroid is
 * a poor substitute for a real address, which is why a same-day booking should send
 * coordinates, but it is better than refusing to quote.
 */
public interface DistrictCentroidRepository {

    Optional<GeoPoint> findByDistrict(String district);
}
