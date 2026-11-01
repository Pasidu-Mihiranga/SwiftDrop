package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;

import java.math.BigDecimal;

/** A district's remote-area surcharge as published by the admin API. */
public record RemoteAreaResponse(String district, BigDecimal flatFee) {

    public static RemoteAreaResponse of(RemoteAreaSurcharge surcharge) {
        return new RemoteAreaResponse(surcharge.district(), surcharge.flatFee());
    }
}
