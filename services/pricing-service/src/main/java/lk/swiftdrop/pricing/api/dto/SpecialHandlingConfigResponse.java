package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;

import java.math.BigDecimal;

/** A handling option as administrators see it, with its fee and whether it is offered. */
public record SpecialHandlingConfigResponse(String code, String label, BigDecimal fee, boolean active,
                                            int sortOrder) {

    public static SpecialHandlingConfigResponse of(SpecialHandlingConfig option) {
        return new SpecialHandlingConfigResponse(option.code(), option.label(), option.fee(), option.active(),
                option.sortOrder());
    }
}
