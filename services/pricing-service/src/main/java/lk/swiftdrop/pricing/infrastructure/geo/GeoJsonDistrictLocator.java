package lk.swiftdrop.pricing.infrastructure.geo;

import lk.swiftdrop.pricing.application.port.out.DistrictLocator;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Answers "which district is this point in?" from the district boundaries bundled with the
 * service ({@code geo/sri-lanka-districts.geojson}, see the NOTICE beside it).
 *
 * <p>The boundaries are loaded once, at start-up, and held in memory: 25 simplified polygons are
 * small, and a lookup then costs a bounding-box check and a ray cast, with no network call and
 * no dependence on a geocoder's idea of an address.
 *
 * <p>Read as plain maps and lists rather than a node tree, so this does not depend on the
 * Jackson tree API, and only Polygon and MultiPolygon are accepted because nothing else
 * describes an area.
 */
@Component
class GeoJsonDistrictLocator implements DistrictLocator {

    static final String RESOURCE = "geo/sri-lanka-districts.geojson";

    private final List<District> districts;

    @Autowired
    GeoJsonDistrictLocator(ObjectMapper mapper) {
        this(load(mapper, new ClassPathResource(RESOURCE)));
    }

    GeoJsonDistrictLocator(List<District> districts) {
        if (districts.isEmpty()) {
            throw new IllegalStateException("No district boundaries were loaded from " + RESOURCE);
        }
        this.districts = List.copyOf(districts);
    }

    @Override
    public Optional<String> districtAt(GeoPoint point) {
        double lon = point.longitude().doubleValue();
        double lat = point.latitude().doubleValue();
        return districts.stream().filter(district -> district.contains(lon, lat)).map(District::name).findFirst();
    }

    int size() {
        return districts.size();
    }

    @SuppressWarnings("unchecked")
    static List<District> load(ObjectMapper mapper, ClassPathResource resource) {
        Map<String, Object> collection;
        try (InputStream in = resource.getInputStream()) {
            collection = mapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read district boundaries " + resource.getPath(), e);
        }
        List<District> result = new ArrayList<>();
        for (Object item : (List<Object>) collection.get("features")) {
            Map<String, Object> feature = (Map<String, Object>) item;
            String name = (String) ((Map<String, Object>) feature.get("properties")).get("district");
            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
            List<Object> coordinates = (List<Object>) geometry.get("coordinates");
            List<Polygon> polygons = switch ((String) geometry.get("type")) {
                case "Polygon" -> List.of(Polygon.of(coordinates));
                case "MultiPolygon" -> coordinates.stream().map(polygon -> Polygon.of((List<Object>) polygon)).toList();
                default -> throw new IllegalStateException("District " + name + " has no area geometry");
            };
            result.add(new District(name, polygons));
        }
        return result;
    }

    /** One district: one or more polygons (an island is a separate polygon). */
    record District(String name, List<Polygon> polygons) {

        boolean contains(double lon, double lat) {
            return polygons.stream().anyMatch(polygon -> polygon.contains(lon, lat));
        }
    }

    /** An outer ring and its holes, each a closed list of [lon, lat] pairs, with a bounding box. */
    record Polygon(double[][] outer, List<double[][]> holes, double minLon, double minLat,
                   double maxLon, double maxLat) {

        @SuppressWarnings("unchecked")
        static Polygon of(List<Object> rings) {
            double[][] outer = ring((List<Object>) rings.get(0));
            List<double[][]> holes = new ArrayList<>();
            for (int i = 1; i < rings.size(); i++) {
                holes.add(ring((List<Object>) rings.get(i)));
            }
            double minLon = Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE;
            double maxLon = -Double.MAX_VALUE;
            double maxLat = -Double.MAX_VALUE;
            for (double[] vertex : outer) {
                minLon = Math.min(minLon, vertex[0]);
                maxLon = Math.max(maxLon, vertex[0]);
                minLat = Math.min(minLat, vertex[1]);
                maxLat = Math.max(maxLat, vertex[1]);
            }
            return new Polygon(outer, List.copyOf(holes), minLon, minLat, maxLon, maxLat);
        }

        @SuppressWarnings("unchecked")
        private static double[][] ring(List<Object> positions) {
            double[][] ring = new double[positions.size()][];
            for (int i = 0; i < positions.size(); i++) {
                List<Number> position = (List<Number>) positions.get(i);
                ring[i] = new double[] {position.get(0).doubleValue(), position.get(1).doubleValue()};
            }
            return ring;
        }

        boolean contains(double lon, double lat) {
            if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) {
                return false;
            }
            if (!inside(outer, lon, lat)) {
                return false;
            }
            return holes.stream().noneMatch(hole -> inside(hole, lon, lat));
        }

        /** Even-odd ray casting. Accurate enough at district scale, where the ground is flat. */
        private static boolean inside(double[][] ring, double lon, double lat) {
            boolean inside = false;
            for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
                double xi = ring[i][0];
                double yi = ring[i][1];
                double xj = ring[j][0];
                double yj = ring[j][1];
                boolean crosses = (yi > lat) != (yj > lat)
                        && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi;
                if (crosses) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }
}
