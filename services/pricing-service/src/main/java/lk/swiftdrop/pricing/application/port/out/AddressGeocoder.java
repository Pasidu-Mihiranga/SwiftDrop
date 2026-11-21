package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.GeoPoint;

import java.util.List;

/** Turns free text such as "45 Galle Road, Colombo" into candidate map points. */
public interface AddressGeocoder {

    /** One candidate place, labelled the way the geocoder describes it. */
    record Match(String label, GeoPoint point) { }

    /**
     * Best candidates first. Empty when nothing matches.
     *
     * @throws lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException when the
     *         geocoder cannot answer now (down, slow or rate limited)
     */
    List<Match> search(String query, int limit);
}
