package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.domain.model.DistanceBand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One published line-haul band and the period its set applies to. */
public record DistanceBandResponse(UUID id, BigDecimal minKm, BigDecimal maxKm, BigDecimal lineHaulFee,
                                   int standardTransitDays, int expressTransitDays,
                                   LocalDate effectiveFrom, LocalDate effectiveTo) {

    public static DistanceBandResponse of(Versioned<DistanceBand> version) {
        DistanceBand band = version.value();
        return new DistanceBandResponse(band.id(), band.minKm(), band.maxKm(), band.lineHaulFee(),
                band.standardTransitDays(), band.expressTransitDays(), version.effectiveFrom(),
                version.effectiveTo());
    }
}
