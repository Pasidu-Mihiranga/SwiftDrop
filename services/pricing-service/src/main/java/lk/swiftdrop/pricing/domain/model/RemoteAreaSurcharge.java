package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What it costs extra to collect from or deliver to one hard-to-reach district.
 *
 * @param effectiveFrom the day it starts; null when only the charge itself matters
 * @param effectiveTo   the exclusive end; null while it is still open
 */
public record RemoteAreaSurcharge(String district, BigDecimal flatFee,
                                  LocalDate effectiveFrom, LocalDate effectiveTo) {

    public RemoteAreaSurcharge(String district, BigDecimal flatFee) {
        this(district, flatFee, null, null);
    }

    public RemoteAreaSurcharge {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("Remote area surcharge needs a district");
        }
        if (flatFee == null || flatFee.signum() < 0) {
            throw new IllegalArgumentException("Remote area surcharge must not be negative");
        }
    }
}
