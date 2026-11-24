package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Free-text address search for the quote and booking maps.
 *
 * <p>Every candidate is checked against the same district boundaries the quote endpoint uses,
 * so a suggestion always names the district a quote will accept for it, and places outside the
 * service area are never offered. The geocoder only proposes points; it never decides a district.
 */
@Service
public class AddressSearchService {

    public static final int MIN_QUERY_LENGTH = 3;
    public static final int MAX_QUERY_LENGTH = 120;
    static final int MAX_SUGGESTIONS = 5;

    private final Optional<AddressGeocoder> geocoder;
    private final CoverageQueryService coverage;

    public AddressSearchService(Optional<AddressGeocoder> geocoder, CoverageQueryService coverage) {
        this.geocoder = geocoder;
        this.coverage = coverage;
    }

    /** A place inside the service area, with the district and zone a quote will use. */
    public record Suggestion(String label, GeoPoint point, String district, String zone) { }

    public List<Suggestion> search(String query) {
        String normalised = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (normalised.length() < MIN_QUERY_LENGTH || normalised.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException("INVALID_ADDRESS_QUERY", "Type between " + MIN_QUERY_LENGTH + " and "
                    + MAX_QUERY_LENGTH + " characters of the address");
        }
        AddressGeocoder search = geocoder.orElseThrow(() ->
                new AddressSearchUnavailableException("Address search is not available. Pick the point on the map."));
        // Ask for more than are shown: candidates outside the service area are dropped below.
        Map<String, Suggestion> byLabel = new LinkedHashMap<>();
        for (AddressGeocoder.Match match : search.search(normalised, MAX_SUGGESTIONS * 2)) {
            coverage.locate(match.point()).ifPresent(located -> byLabel.putIfAbsent(
                    match.label().strip().toLowerCase(Locale.ROOT),
                    new Suggestion(match.label().strip(), match.point(), located.district(), located.zone().value())));
            if (byLabel.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(byLabel.values());
    }
}
