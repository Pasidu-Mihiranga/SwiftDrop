package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;
import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.application.port.out.DistanceBandAdminRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingAdminRepository;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.DistanceBandSchedule;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Maintains the configuration V16 introduced: the fee-wide settings (fuel, tax, minimum charge
 * and the same-day cut-off), the line-haul distance bands, the special handling options and the
 * same-day vehicles. Restricted to administrators at the API boundary.
 *
 * <p>Settings and bands are versioned like rate cards: a change is published from a date, closes
 * what it replaces and never rewrites it. The date may not be in the past, so a change can never
 * re-price a day that has already been quoted, and must follow every version already published,
 * so a mistake is corrected from a later date. Handling options and vehicles are edited in place,
 * as size classes are: an issued quote keeps its own copy of the fee and limits it was given.
 */
@Service
public class PricingConfigurationAdminService {

    private final PricingSettingsAdminRepository settings;
    private final DistanceBandAdminRepository bands;
    private final SpecialHandlingAdminRepository handling;
    private final SameDayVehicleAdminRepository vehicles;
    private final Clock clock;

    public PricingConfigurationAdminService(PricingSettingsAdminRepository settings,
                                            DistanceBandAdminRepository bands,
                                            SpecialHandlingAdminRepository handling,
                                            SameDayVehicleAdminRepository vehicles, Clock clock) {
        this.settings = settings;
        this.bands = bands;
        this.handling = handling;
        this.vehicles = vehicles;
        this.clock = clock;
    }

    /** One band as entered on the admin form, before it is checked. */
    public record BandDraft(BigDecimal minKm, BigDecimal maxKm, BigDecimal lineHaulFee,
                            int standardTransitDays, int expressTransitDays) {
    }

    // ---------------------------------------------------------------- fee settings

    @Transactional(readOnly = true)
    public List<Versioned<PricingSettings>> settingsHistory() {
        return settings.findAllVersions();
    }

    @Transactional
    public Versioned<PricingSettings> publishSettings(BigDecimal fuelSurchargePct, BigDecimal taxPct,
                                                      BigDecimal minimumCharge, LocalTime sameDayCutoff,
                                                      LocalDate effectiveFrom) {
        requireNotPast(effectiveFrom);
        if (settings.hasVersionStartingOnOrAfter(effectiveFrom)) {
            throw new ConflictException("PRICING_SETTINGS_PERIOD_CONFLICT",
                    "Fee settings already start on or after " + effectiveFrom + ". Publish from a later date.");
        }
        PricingSettings value = valid("INVALID_PRICING_SETTINGS",
                () -> new PricingSettings(null, fuelSurchargePct, taxPct, minimumCharge, sameDayCutoff));
        return settings.publish(value, effectiveFrom);
    }

    // ---------------------------------------------------------------- distance bands

    @Transactional(readOnly = true)
    public List<Versioned<DistanceBand>> bandHistory() {
        return bands.findAllBandVersions();
    }

    /** Replaces the whole set from a date: a set that leaves a gap would leave distances unpriced. */
    @Transactional
    public List<Versioned<DistanceBand>> publishBands(List<BandDraft> drafts, LocalDate effectiveFrom) {
        requireNotPast(effectiveFrom);
        if (bands.hasBandVersionStartingOnOrAfter(effectiveFrom)) {
            throw new ConflictException("DISTANCE_BAND_PERIOD_CONFLICT",
                    "Distance bands already start on or after " + effectiveFrom + ". Publish from a later date.");
        }
        List<DistanceBand> complete = valid("INVALID_DISTANCE_BANDS", () -> DistanceBandSchedule.requireComplete(
                drafts.stream()
                        .map(draft -> new DistanceBand(null, draft.minKm(), draft.maxKm(), draft.lineHaulFee(),
                                draft.standardTransitDays(), draft.expressTransitDays()))
                        .toList()));
        return bands.publishBands(complete, effectiveFrom);
    }

    // ---------------------------------------------------------------- handling options

    @Transactional(readOnly = true)
    public List<SpecialHandlingConfig> handlingOptions() {
        return handling.findAll();
    }

    @Transactional
    public SpecialHandlingConfig addHandling(String code, String label, BigDecimal fee, int sortOrder) {
        String normalised = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        SpecialHandlingConfig option = valid("INVALID_SPECIAL_HANDLING",
                () -> new SpecialHandlingConfig(normalised, label, fee, true, sortOrder));
        if (handling.findByCode(option.code()).isPresent()) {
            throw new ConflictException("SPECIAL_HANDLING_EXISTS",
                    "A handling option " + option.code() + " already exists. Edit it instead.");
        }
        return handling.save(option);
    }

    /** Retiring an option ({@code active = false}) hides it from new quotes; it is never deleted. */
    @Transactional
    public SpecialHandlingConfig updateHandling(String code, String label, BigDecimal fee, boolean active,
                                                int sortOrder) {
        SpecialHandlingConfig current = handling.findByCode(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Special handling option", code));
        return handling.save(valid("INVALID_SPECIAL_HANDLING",
                () -> new SpecialHandlingConfig(current.code(), label, fee, active, sortOrder)));
    }

    // ---------------------------------------------------------------- same-day vehicles

    @Transactional(readOnly = true)
    public List<SameDayVehicleConfig> sameDayVehicles() {
        return vehicles.findAllVehicles();
    }

    @Transactional
    public SameDayVehicleConfig updateVehicle(VehicleType type, String label, BigDecimal maxWeightKg,
                                              BigDecimal maxDistanceKm, BigDecimal rateMultiplier, boolean active) {
        vehicles.findByType(type).orElseThrow(() -> ResourceNotFoundException.of("Same-day vehicle", type));
        return vehicles.save(valid("INVALID_SAME_DAY_VEHICLE", () -> new SameDayVehicleConfig(
                new SameDayVehicle(type, maxWeightKg, maxDistanceKm, rateMultiplier), label, active)));
    }

    // ---------------------------------------------------------------- shared rules

    private void requireNotPast(LocalDate effectiveFrom) {
        LocalDate today = LocalDate.now(clock);
        if (effectiveFrom == null || effectiveFrom.isBefore(today)) {
            throw new BusinessRuleViolationException("EFFECTIVE_DATE_IN_PAST",
                    "A change takes effect today (" + today + ") or later, never on a day already quoted");
        }
    }

    /** Turns a model's own argument checks into a 422 rather than a 500. */
    private static <T> T valid(String code, Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new BusinessRuleViolationException(code, invalid.getMessage());
        }
    }
}
