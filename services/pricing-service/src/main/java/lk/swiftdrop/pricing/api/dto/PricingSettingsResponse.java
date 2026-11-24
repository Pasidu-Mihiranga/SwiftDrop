package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** One published version of the fee-wide settings. */
public record PricingSettingsResponse(UUID id, BigDecimal fuelSurchargePct, BigDecimal taxPct,
                                      BigDecimal minimumCharge, LocalTime sameDayCutoff,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {

    public static PricingSettingsResponse of(Versioned<PricingSettings> version) {
        PricingSettings value = version.value();
        return new PricingSettingsResponse(value.id(), value.fuelSurchargePct(), value.taxPct(),
                value.minimumCharge(), value.sameDayCutoff(), version.effectiveFrom(), version.effectiveTo());
    }
}
