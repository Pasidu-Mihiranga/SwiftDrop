package lk.swiftdrop.merchant.domain.model;

import java.util.Objects;

/**
 * Where couriers collect the merchant's parcels. Personal data: it is encrypted at rest and
 * never written to logs or error messages.
 *
 * @param line2      optional
 * @param postalCode optional
 */
public record PickupAddress(String line1, String line2, String city, String district, String postalCode) {

    public PickupAddress {
        Objects.requireNonNull(line1, "line1");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(district, "district");
    }

    /** Keeps the value out of logs if the address is ever printed by accident. */
    @Override
    public String toString() {
        return "PickupAddress[masked]";
    }
}
