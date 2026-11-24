package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.pricing.application.command.PricingConfigurationAdminService.BandDraft;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A complete replacement set of line-haul bands, published from a date. */
public record DistanceBandSetRequest(
        @NotNull LocalDate effectiveFrom,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull Band> bands) {

    /** @param maxKm exclusive; null for the open-ended last band */
    public record Band(
            @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal minKm,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal maxKm,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal lineHaulFee,
            @Min(1) @Max(30) int standardTransitDays,
            @Min(1) @Max(30) int expressTransitDays) {

        BandDraft toDraft() {
            return new BandDraft(minKm, maxKm, lineHaulFee, standardTransitDays, expressTransitDays);
        }
    }

    public List<BandDraft> drafts() {
        return bands.stream().map(Band::toDraft).toList();
    }
}
