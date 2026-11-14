package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/** A point on the ground, in decimal degrees. */
public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    public GeoPoint {
        require(latitude, MAX_LATITUDE, "Latitude");
        require(longitude, MAX_LONGITUDE, "Longitude");
    }

    private static void require(BigDecimal value, BigDecimal limit, String name) {
        if (value == null || value.abs().compareTo(limit) > 0) {
            throw new IllegalArgumentException(name + " is out of range");
        }
    }

    public static GeoPoint of(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
