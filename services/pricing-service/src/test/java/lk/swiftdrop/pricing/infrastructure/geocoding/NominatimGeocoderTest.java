package lk.swiftdrop.pricing.infrastructure.geocoding;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lk.swiftdrop.pricing.application.port.out.AddressGeocoder.Match;
import lk.swiftdrop.pricing.application.query.AddressSearchUnavailableException;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NominatimGeocoderTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final GeocodeCache cache = mock(GeocodeCache.class);
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://nominatim:8080").defaultHeader("User-Agent", "test-agent/1.0");
        server = MockRestServiceServer.bindTo(builder).build();
        when(cache.find(anyString(), anyInt())).thenReturn(Optional.empty());
    }

    private static GeocodingProperties settings(String email) {
        return new GeocodingProperties("http://nominatim:8080", "test-agent/1.0", email, "lk", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofHours(1), Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    private NominatimGeocoder geocoder(String email, CircuitBreaker breaker) {
        return new NominatimGeocoder(builder.build(), MAPPER, settings(email), cache, breaker,
                new MinimumInterval(Duration.ZERO, Duration.ofSeconds(1), Clock.systemUTC()));
    }

    private NominatimGeocoder geocoder() {
        return geocoder("", CircuitBreaker.ofDefaults("test"));
    }

    @Test
    void searchesInTheConfiguredCountryAndReadsStringCoordinates() {
        server.expect(requestTo("http://nominatim:8080/search?q=45%20Galle%20Road&format=jsonv2&limit=10"
                        + "&addressdetails=0&countrycodes=lk"))
                .andExpect(header("User-Agent", "test-agent/1.0"))
                .andRespond(withSuccess("""
                        [{"place_id":1,"display_name":"45, Galle Road, Colombo 03, Sri Lanka",
                          "lat":"6.9100","lon":"79.8510","importance":0.4}]
                        """, MediaType.APPLICATION_JSON));

        List<Match> matches = geocoder().search("45 Galle Road", 10);

        assertThat(matches).containsExactly(new Match("45, Galle Road, Colombo 03, Sri Lanka",
                GeoPoint.of("6.9100", "79.8510")));
        verify(cache).put("45 Galle Road", 10, matches);
        server.verify();
    }

    @Test
    void passesTheContactAddressWhenOneIsConfigured() {
        server.expect(requestTo("http://nominatim:8080/search?q=Kandy&format=jsonv2&limit=5&addressdetails=0"
                        + "&countrycodes=lk&email=ops%40example.test"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(geocoder("ops@example.test", CircuitBreaker.ofDefaults("test")).search("Kandy", 5)).isEmpty();
        server.verify();
    }

    @Test
    void answersFromTheCacheWithoutCallingTheGeocoder() {
        List<Match> cached = List.of(new Match("Kandy", GeoPoint.of("7.29", "80.63")));
        when(cache.find("Kandy", 5)).thenReturn(Optional.of(cached));

        assertThat(geocoder().search("Kandy", 5)).isEqualTo(cached);
        server.verify();
        verify(cache, never()).put(anyString(), anyInt(), any());
    }

    @Test
    void skipsPlacesWithMissingOrImpossibleCoordinates() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andRespond(withSuccess("""
                        [{"display_name":"No coordinates"},
                         {"display_name":"Off the planet","lat":"95","lon":"80"},
                         {"display_name":"","lat":"7","lon":"80"},
                         {"display_name":"Kandy","lat":"7.29","lon":"80.63"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(geocoder().search("Kandy", 5)).extracting(Match::label).containsExactly("Kandy");
    }

    @Test
    void reportsAnUnreachableOrFailingGeocoderAsUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andRespond(withException(new IOException("connection refused")));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andRespond(withSuccess("<html>not json</html>", MediaType.TEXT_HTML));

        NominatimGeocoder geocoder = geocoder();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> geocoder.search("Kandy", 5))
                    .isInstanceOfSatisfying(AddressSearchUnavailableException.class,
                            error -> assertThat(error.code()).isEqualTo("ADDRESS_SEARCH_UNAVAILABLE"));
        }
        verify(cache, never()).put(anyString(), anyInt(), any());
    }

    @Test
    void stopsCallingAGeocoderThatKeepsFailing() {
        CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                .recordExceptions(AddressSearchUnavailableException.class).build());
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                        requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        NominatimGeocoder geocoder = geocoder("", breaker);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> geocoder.search("Kandy", 5)).isInstanceOf(AddressSearchUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }
}
