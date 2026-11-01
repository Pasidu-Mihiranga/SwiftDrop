package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/** What it costs extra to collect from or deliver to one hard-to-reach district. */
public record RemoteAreaSurcharge(String district, BigDecimal flatFee) {

    public RemoteAreaSurcharge {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("Remote area surcharge needs a district");
        }
        if (flatFee == null || flatFee.signum() < 0) {
            throw new IllegalArgumentException("Remote area surcharge must not be negative");
        }
    }
}
