package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;
import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.application.port.out.DistanceBandAdminRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleAdminRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingAdminRepository;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA adapters for maintaining the V16 configuration from the admin screens. */
@Repository
class PricingExtensionAdminAdapters implements PricingSettingsAdminRepository, DistanceBandAdminRepository,
        SpecialHandlingAdminRepository, SameDayVehicleAdminRepository {

    private final PricingSettingJpaRepository settings;
    private final DistanceBandJpaRepository bands;
    private final SpecialHandlingJpaRepository handling;
    private final SameDayVehicleJpaRepository vehicles;

    PricingExtensionAdminAdapters(PricingSettingJpaRepository settings, DistanceBandJpaRepository bands,
                                  SpecialHandlingJpaRepository handling, SameDayVehicleJpaRepository vehicles) {
        this.settings = settings;
        this.bands = bands;
        this.handling = handling;
        this.vehicles = vehicles;
    }

    // ---------------------------------------------------------------- fee settings

    @Override
    public List<Versioned<PricingSettings>> findAllVersions() {
        return settings.findAllByOrderByEffectiveFromDesc().stream().map(PricingExtensionAdminAdapters::versioned)
                .toList();
    }

    @Override
    public boolean hasVersionStartingOnOrAfter(LocalDate day) {
        return settings.existsByEffectiveFromGreaterThanEqual(day);
    }

    @Override
    public Versioned<PricingSettings> publish(PricingSettings value, LocalDate effectiveFrom) {
        // Close what is in force on the new start so the periods meet without overlapping, and the
        // old row survives for an already-issued quote's pricing version to refer to.
        settings.findInForce(effectiveFrom).forEach(row -> row.closeOn(effectiveFrom));
        PricingExtensionEntities.Setting saved = settings.save(new PricingExtensionEntities.Setting(
                UUID.randomUUID(), value.fuelSurchargePct(), value.taxPct(), value.minimumCharge(),
                value.sameDayCutoff(), effectiveFrom));
        return versioned(saved);
    }

    private static Versioned<PricingSettings> versioned(PricingExtensionEntities.Setting row) {
        return new Versioned<>(PricingExtensionAdapters.toDomain(row), row.getEffectiveFrom(), row.getEffectiveTo());
    }

    // ---------------------------------------------------------------- distance bands

    @Override
    public List<Versioned<DistanceBand>> findAllBandVersions() {
        return bands.findAllByOrderByEffectiveFromDescMinKmAsc().stream().map(PricingExtensionAdminAdapters::versioned)
                .toList();
    }

    @Override
    public boolean hasBandVersionStartingOnOrAfter(LocalDate day) {
        return bands.existsByEffectiveFromGreaterThanEqual(day);
    }

    @Override
    public List<Versioned<DistanceBand>> publishBands(List<DistanceBand> set, LocalDate effectiveFrom) {
        bands.findInForce(effectiveFrom).forEach(row -> row.closeOn(effectiveFrom));
        bands.flush();
        return set.stream()
                .map(band -> bands.save(new PricingExtensionEntities.Band(UUID.randomUUID(), band.minKm(),
                        band.maxKm(), band.lineHaulFee(), (short) band.standardTransitDays(),
                        (short) band.expressTransitDays(), effectiveFrom)))
                .map(PricingExtensionAdminAdapters::versioned)
                .toList();
    }

    private static Versioned<DistanceBand> versioned(PricingExtensionEntities.Band row) {
        return new Versioned<>(PricingExtensionAdapters.toDomain(row), row.getEffectiveFrom(), row.getEffectiveTo());
    }

    // ---------------------------------------------------------------- handling options

    @Override
    public List<SpecialHandlingConfig> findAll() {
        return handling.findAllByOrderBySortOrderAscCodeAsc().stream().map(PricingExtensionAdminAdapters::toConfig)
                .toList();
    }

    @Override
    public Optional<SpecialHandlingConfig> findByCode(String code) {
        return handling.findById(code).map(PricingExtensionAdminAdapters::toConfig);
    }

    @Override
    public SpecialHandlingConfig save(SpecialHandlingConfig option) {
        PricingExtensionEntities.Handling row = handling.findById(option.code())
                .orElseGet(() -> new PricingExtensionEntities.Handling(option.code()));
        row.update(option.label(), option.fee(), option.active(), (short) option.sortOrder());
        return toConfig(handling.save(row));
    }

    private static SpecialHandlingConfig toConfig(PricingExtensionEntities.Handling row) {
        return new SpecialHandlingConfig(row.getCode(), row.getLabel(), row.getFee(), row.isActive(),
                row.getSortOrder());
    }

    // ---------------------------------------------------------------- same-day vehicles

    @Override
    public List<SameDayVehicleConfig> findAllVehicles() {
        return vehicles.findAllByOrderByCodeAsc().stream().map(PricingExtensionAdminAdapters::toConfig).toList();
    }

    @Override
    public Optional<SameDayVehicleConfig> findByType(VehicleType type) {
        return vehicles.findById(type.name()).map(PricingExtensionAdminAdapters::toConfig);
    }

    @Override
    public SameDayVehicleConfig save(SameDayVehicleConfig vehicle) {
        // The vehicle types are fixed by a check constraint, so an update never creates a row.
        PricingExtensionEntities.Vehicle row = vehicles.findById(vehicle.type().name())
                .orElseThrow(() -> new IllegalStateException("No row for vehicle " + vehicle.type()));
        row.update(vehicle.label(), vehicle.limits().maxWeightKg(), vehicle.limits().maxDistanceKm(),
                vehicle.limits().rateMultiplier(), vehicle.active());
        return toConfig(vehicles.save(row));
    }

    private static SameDayVehicleConfig toConfig(PricingExtensionEntities.Vehicle row) {
        return new SameDayVehicleConfig(PricingExtensionAdapters.toDomain(row), row.getLabel(), row.isActive());
    }
}
