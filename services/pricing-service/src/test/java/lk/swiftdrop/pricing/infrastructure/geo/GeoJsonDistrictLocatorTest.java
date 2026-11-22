package lk.swiftdrop.pricing.infrastructure.geo;

import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bundled boundaries, checked against towns whose district nobody disputes. */
class GeoJsonDistrictLocatorTest {

    private static final GeoJsonDistrictLocator LOCATOR = new GeoJsonDistrictLocator(JsonMapper.builder().build());

    @Test
    void loadsAllTwentyFiveDistricts() {
        assertThat(LOCATOR.size()).isEqualTo(25);
    }

    @ParameterizedTest(name = "{0}, {1} is in {2}")
    @CsvSource({
            "6.9344, 79.8428, Colombo",        // Fort
            "7.0873, 79.9990, Gampaha",
            "7.2936, 80.6413, Kandy",
            "6.9497, 80.7891, Nuwara Eliya",
            "6.0329, 80.2168, Galle",
            "9.6615, 80.0255, Jaffna",
            "7.2975, 81.6820, Ampara",
            "7.7310, 81.6747, Batticaloa",
            "8.3114, 80.4037, Anuradhapura",
            "6.9934, 81.0550, Badulla",
            "6.6828, 80.3992, Ratnapura",
            "7.4863, 80.3623, Kurunegala",
    })
    void findsTheDistrictATownIsIn(String latitude, String longitude, String district) {
        assertThat(LOCATOR.districtAt(GeoPoint.of(latitude, longitude))).contains(district);
    }

    @Test
    void aPointAtSeaIsOutsideCoverage() {
        assertThat(LOCATOR.districtAt(GeoPoint.of("6.9", "79.5"))).isEmpty();
        assertThat(LOCATOR.districtAt(GeoPoint.of("13.0", "80.2"))).isEmpty();   // Chennai
    }

    @Test
    void respectsAHoleInAPolygon() {
        // A square with a square hole: the hole is not in the district.
        String json = """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                 "properties":{"district":"Donut"},
                 "geometry":{"type":"Polygon","coordinates":[
                   [[0,0],[10,0],[10,10],[0,10],[0,0]],
                   [[4,4],[6,4],[6,6],[4,6],[4,4]]]}}]}
                """;
        var mapper = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        var collection = (java.util.Map<String, Object>) mapper.readValue(json, java.util.Map.class);
        var feature = (java.util.Map<String, Object>) ((List<Object>) collection.get("features")).getFirst();
        var geometry = (java.util.Map<String, Object>) feature.get("geometry");
        @SuppressWarnings("unchecked")
        var polygon = GeoJsonDistrictLocator.Polygon.of((List<Object>) geometry.get("coordinates"));
        var locator = new GeoJsonDistrictLocator(List.of(new GeoJsonDistrictLocator.District("Donut",
                List.of(polygon))));

        assertThat(locator.districtAt(GeoPoint.of("2", "2"))).contains("Donut");
        assertThat(locator.districtAt(GeoPoint.of("5", "5"))).isEmpty();
        assertThat(locator.districtAt(GeoPoint.of("5", "11"))).isEmpty();
    }

    @Test
    void refusesToStartWithNoBoundaries() {
        assertThatThrownBy(() -> new GeoJsonDistrictLocator(List.of())).isInstanceOf(IllegalStateException.class);
    }
}
