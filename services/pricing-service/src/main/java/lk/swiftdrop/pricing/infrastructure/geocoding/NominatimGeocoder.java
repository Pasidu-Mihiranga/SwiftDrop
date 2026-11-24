package lk.swiftdrop.pricing.infrastructure.geocoding;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder;
import lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Searches a Nominatim server ({@code /search?format=jsonv2}), limited to the configured
 * countries.
 *
 * <p>Answers are cached by a hash of the query, because the public server asks clients to cache
 * and an address typed twice should not be sent twice. Calls are spaced by
 * {@link MinimumInterval} and guarded by a circuit breaker, so a slow geocoder costs a customer
 * one short wait and then a plain "search unavailable", never a hung form.
 */
class NominatimGeocoder implements AddressGeocoder {

    private static final TypeReference<List<Place>> PLACES = new TypeReference<>() { };

    private final org.springframework.web.client.RestClient nominatim;
    private final ObjectMapper mapper;
    private final GeocodingProperties settings;
    private final GeocodeCache cache;
    private final CircuitBreaker breaker;
    private final MinimumInterval interval;

    NominatimGeocoder(org.springframework.web.client.RestClient nominatim, ObjectMapper mapper,
                      GeocodingProperties settings, GeocodeCache cache, CircuitBreaker breaker,
                      MinimumInterval interval) {
        this.nominatim = nominatim;
        this.mapper = mapper;
        this.settings = settings;
        this.cache = cache;
        this.breaker = breaker;
        this.interval = interval;
    }

    @Override
    public List<Match> search(String query, int limit) {
        Optional<List<Match>> cached = cache.find(query, limit);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<Match> matches;
        try {
            matches = breaker.executeSupplier(() -> {
                interval.acquire();
                return fetch(query, limit);
            });
        } catch (CallNotPermittedException open) {
            throw new AddressSearchUnavailableException("Address search is unavailable just now. Pick the point on"
                    + " the map.", open);
        }
        cache.put(query, limit, matches);
        return matches;
    }

    private List<Match> fetch(String query, int limit) {
        String body;
        try {
            body = nominatim.get().uri(builder -> uri(builder, query, limit)).retrieve().body(String.class);
        } catch (RestClientException failed) {
            throw new AddressSearchUnavailableException("Address search is unavailable just now. Pick the point on"
                    + " the map.", failed);
        }
        try {
            List<Place> places = body == null ? List.of() : mapper.readValue(body, PLACES);
            List<Match> matches = new ArrayList<>();
            for (Place place : places) {
                toMatch(place).ifPresent(matches::add);
            }
            return List.copyOf(matches);
        } catch (JacksonException unreadable) {
            throw new AddressSearchUnavailableException("The address search answer could not be read.", unreadable);
        }
    }

    private URI uri(UriBuilder builder, String query, int limit) {
        builder.path("/search")
                .queryParam("q", "{q}")
                .queryParam("format", "jsonv2")
                .queryParam("limit", limit)
                .queryParam("addressdetails", 0);
        if (settings.countryCodes() != null && !settings.countryCodes().isBlank()) {
            builder.queryParam("countrycodes", settings.countryCodes().strip());
        }
        if (settings.email() != null && !settings.email().isBlank()) {
            builder.queryParam("email", "{email}");
            return builder.build(query, settings.email().strip());
        }
        return builder.build(query);
    }

    /** A place with unusable coordinates is skipped rather than failing the whole search. */
    private static Optional<Match> toMatch(Place place) {
        if (place.displayName() == null || place.displayName().isBlank() || place.lat() == null
                || place.lon() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Match(place.displayName(),
                    new GeoPoint(new BigDecimal(place.lat()), new BigDecimal(place.lon()))));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** Only what is read; Nominatim sends coordinates as strings. */
    record Place(@com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,
                 String lat, String lon) { }
}
