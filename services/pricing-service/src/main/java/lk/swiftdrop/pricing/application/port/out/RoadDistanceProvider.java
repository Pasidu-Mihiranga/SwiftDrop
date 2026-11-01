package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.LaneDistance;

/**
 * How far it is to drive between two points.
 *
 * <p>Never throws. Degrading to an estimate is part of the contract rather than something each
 * caller has to handle, because a routing engine being slow or absent must not take the public
 * quote endpoint down with it. What the answer is based on travels back in the result, so an
 * estimated quote can be told from a measured one afterwards.
 */
public interface RoadDistanceProvider {

    LaneDistance between(GeoPoint origin, GeoPoint destination);
}
