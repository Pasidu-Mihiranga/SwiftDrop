package lk.swiftdrop.pricing.infrastructure.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * A Nominatim-compatible geocoder. Blank {@code url} turns address search off.
 *
 * @param userAgent   sent on every request; public Nominatim refuses anonymous clients
 * @param email       optional contact address passed as {@code email}, as Nominatim asks of
 *                    heavier users
 * @param minInterval least time between two calls from this instance (public Nominatim allows
 *                    one a second)
 * @param maxWait     how long a search may queue behind that interval before it is refused
 */
@ConfigurationProperties("swiftdrop.pricing.geocoding")
public record GeocodingProperties(String url, String userAgent, String email, String countryCodes,
                                  Duration connectTimeout, Duration readTimeout, Duration cacheTtl,
                                  Duration minInterval, Duration maxWait, Duration breakerOpenFor) {
}
