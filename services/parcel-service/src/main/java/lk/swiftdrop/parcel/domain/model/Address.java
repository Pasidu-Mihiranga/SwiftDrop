package lk.swiftdrop.parcel.domain.model;

import java.math.BigDecimal;

/**
 * A pickup or drop-off address. Personal data: it is encrypted at rest and only its city, district
 * and zone ever travel on Kafka.
 */
public record Address(String line1, String line2, String city, String district, String postalCode,
                      BigDecimal lat, BigDecimal lng) {
}
