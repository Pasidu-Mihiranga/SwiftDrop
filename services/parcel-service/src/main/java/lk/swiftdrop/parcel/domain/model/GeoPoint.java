package lk.swiftdrop.parcel.domain.model;

import java.math.BigDecimal;

/** A courier's position when a status changed. Stored with the history; never published on Kafka. */
public record GeoPoint(BigDecimal lat, BigDecimal lng) {

    public GeoPoint {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("A position needs both latitude and longitude");
        }
        if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Latitude or longitude is out of range");
        }
    }
}
