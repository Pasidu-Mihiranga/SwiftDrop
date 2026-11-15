package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A district's remote-area surcharge as published by the admin API.
 *
 * @param effectiveTo exclusive end date; null while it is still open
 */
public record RemoteAreaResponse(String district, BigDecimal flatFee,
                                 LocalDate effectiveFrom, LocalDate effectiveTo) {

    public static RemoteAreaResponse of(RemoteAreaSurcharge surcharge) {
        return new RemoteAreaResponse(surcharge.district(), surcharge.flatFee(),
                surcharge.effectiveFrom(), surcharge.effectiveTo());
    }
}
