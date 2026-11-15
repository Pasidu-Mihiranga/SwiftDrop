package lk.swiftdrop.pricing.infrastructure.distance;

import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Asks OSRM how far it is to drive between two points.
 *
 * <p>OSRM reports "no route" with a 400 and a code in the body. That is an answer about the
 * map, not a fault in the router, so it comes back as empty and must not count against the
 * circuit breaker. Everything else that goes wrong is a {@link RoutingUnavailableException}.
 */
class OsrmClient {

    private static final Set<String> NO_ROUTE_CODES = Set.of("NoRoute", "NoSegment");
    private static final BigDecimal METRES_PER_KM = BigDecimal.valueOf(1000);

    private final RestClient osrm;
    private final ObjectMapper mapper;

    OsrmClient(RestClient osrm, ObjectMapper mapper) {
        this.osrm = osrm;
        this.mapper = mapper;
    }

    /** The driving distance in kilometres, or empty when the map has no route between them. */
    Optional<BigDecimal> drivingKm(GeoPoint origin, GeoPoint destination) {
        Exchange exchange;
        try {
            exchange = osrm.get()
                    // OSRM takes longitude first, the reverse of how the points are stored.
                    .uri("/route/v1/driving/{fromLon},{fromLat};{toLon},{toLat}?overview=false&steps=false",
                            origin.longitude().toPlainString(), origin.latitude().toPlainString(),
                            destination.longitude().toPlainString(), destination.latitude().toPlainString())
                    .exchange((request, response) -> new Exchange(response.getStatusCode(),
                            new String(response.getBody().readAllBytes())));
        } catch (RestClientException failed) {
            throw new RoutingUnavailableException("Could not reach the router", failed);
        }
        return read(exchange);
    }

    private Optional<BigDecimal> read(Exchange exchange) {
        OsrmResponse body = parse(exchange);
        if (body != null && NO_ROUTE_CODES.contains(body.code())) {
            return Optional.empty();
        }
        if (!exchange.status().is2xxSuccessful() || body == null || !"Ok".equals(body.code())
                || body.routes() == null || body.routes().isEmpty()) {
            throw new RoutingUnavailableException("Router answered " + exchange.status());
        }
        return Optional.of(BigDecimal.valueOf(body.routes().get(0).distance())
                .divide(METRES_PER_KM, 2, RoundingMode.HALF_UP));
    }

    private OsrmResponse parse(Exchange exchange) {
        try {
            return mapper.readValue(exchange.body(), OsrmResponse.class);
        } catch (JacksonException unreadable) {
            if (exchange.status().is2xxSuccessful()) {
                throw new RoutingUnavailableException("Router sent an unreadable body", unreadable);
            }
            return null;
        }
    }

    private record Exchange(HttpStatusCode status, String body) { }

    /** Only what is read. Unknown fields are ignored by the application mapper. */
    record OsrmResponse(String code, List<Route> routes) {

        record Route(double distance) { }
    }
}
