package lk.swiftdrop.pricing.infrastructure.distance;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmClientTest {

    private static final GeoPoint COLOMBO = GeoPoint.of("6.927", "79.861");
    private static final GeoPoint KANDY = GeoPoint.of("7.291", "80.634");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private MockRestServiceServer server;
    private OsrmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://osrm:5000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OsrmClient(builder.build(), MAPPER);
    }

    @Test
    void sendsLongitudeFirstAndConvertsMetresToKilometres() {
        server.expect(requestTo("http://osrm:5000/route/v1/driving/79.861,6.927;80.634,7.291?overview=false&steps=false"))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"distance":115234.6,"duration":9000.1}],"waypoints":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.drivingKm(COLOMBO, KANDY)).hasValueSatisfying(
                km -> assertThat(km).isEqualByComparingTo("115.23"));
        server.verify();
    }

    @Test
    void treatsNoRouteAsAnAnswerAboutTheMapRatherThanAFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NoRoute\",\"message\":\"Impossible route\"}"));

        assertThat(client.drivingKm(COLOMBO, KANDY)).isEmpty();
    }

    @Test
    void treatsAPointOffTheMapTheSameWay() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NoSegment\"}"));

        assertThat(client.drivingKm(COLOMBO, KANDY)).isEmpty();
    }

    @Test
    void reportsABadRequestThatIsNotAboutTheMapAsAFault() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"InvalidUrl\"}"));

        assertThatThrownBy(() -> client.drivingKm(COLOMBO, KANDY))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void reportsAServerErrorWithAnHtmlBodyAsAFault() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad gateway</html>"));

        assertThatThrownBy(() -> client.drivingKm(COLOMBO, KANDY))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void reportsAnUnreadableSuccessAsAFault() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.drivingKm(COLOMBO, KANDY))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void reportsASuccessWithNoRoutesAsAFault() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.drivingKm(COLOMBO, KANDY))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void reportsAConnectionFailureAsAFault() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/route/v1/driving/")))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.drivingKm(COLOMBO, KANDY))
                .isInstanceOf(RoutingUnavailableException.class);
    }
}
