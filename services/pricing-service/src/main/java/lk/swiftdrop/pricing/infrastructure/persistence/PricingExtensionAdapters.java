package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.DistanceBandRepository;
import lk.swiftdrop.pricing.application.port.out.PricingSettingsRepository;
import lk.swiftdrop.pricing.application.port.out.SameDayVehicleRepository;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingRepository;
import lk.swiftdrop.pricing.domain.model.DistanceBand;
import lk.swiftdrop.pricing.domain.model.PricingSettings;
import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** JPA adapters for the configuration tables V16 adds. */
@Repository
class PricingExtensionAdapters implements PricingSettingsRepository, DistanceBandRepository,
        SpecialHandlingRepository, SameDayVehicleRepository {

    private final PricingSettingJpaRepository settings;
    private final DistanceBandJpaRepository bands;
    private final SpecialHandlingJpaRepository handling;
    private final SameDayVehicleJpaRepository vehicles;

    PricingExtensionAdapters(PricingSettingJpaRepository settings, DistanceBandJpaRepository bands,
                             SpecialHandlingJpaRepository handling, SameDayVehicleJpaRepository vehicles) {
        this.settings = settings;
        this.bands = bands;
        this.handling = handling;
        this.vehicles = vehicles;
    }

    @Override
    public Optional<PricingSettings> findInForce(LocalDate on) {
        // effective_from is unique, so the newest row in force is unambiguous.
        return settings.findInForce(on).stream().findFirst()
                .map(PricingExtensionAdapters::toDomain);
    }

    /**
     * The newest version of each band. A band that has been re-priced but whose predecessor was
     * not closed would otherwise appear twice; the later start wins, as it does for rate cards.
     */
    @Override
    public List<DistanceBand> findBandsInForce(LocalDate on) {
        Set<BigDecimal> seenStarts = new HashSet<>();
        List<DistanceBand> result = new ArrayList<>();
        for (PricingExtensionEntities.Band row : bands.findInForce(on)) {
            // BigDecimal equality is scale-sensitive; compare on a normalised start.
            if (seenStarts.add(row.getMinKm().stripTrailingZeros())) {
                result.add(toDomain(row));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<SpecialHandlingOption> findActive() {
        return handling.findByActiveTrueOrderBySortOrderAscCodeAsc().stream()
                .map(row -> new SpecialHandlingOption(row.getCode(), row.getLabel(), row.getFee()))
                .toList();
    }

    @Override
    public Optional<SameDayVehicle> findActive(VehicleType type) {
        return vehicles.findByCodeAndActiveTrue(type.name()).map(PricingExtensionAdapters::toDomain);
    }

    @Override
    public List<SameDayVehicle> findAllActive() {
        return vehicles.findByActiveTrueOrderByCodeAsc().stream().map(PricingExtensionAdapters::toDomain).toList();
    }

    static PricingSettings toDomain(PricingExtensionEntities.Setting row) {
        return new PricingSettings(row.getId(), row.getFuelSurchargePct(), row.getTaxPct(), row.getMinimumCharge(),
                row.getSameDayCutoff());
    }

    static DistanceBand toDomain(PricingExtensionEntities.Band row) {
        return new DistanceBand(row.getId(), row.getMinKm(), row.getMaxKm(), row.getLineHaulFee(),
                row.getStandardTransitDays(), row.getExpressTransitDays());
    }

    static SameDayVehicle toDomain(PricingExtensionEntities.Vehicle row) {
        return new SameDayVehicle(VehicleType.valueOf(row.getCode()), row.getMaxWeightKg(), row.getMaxDistanceKm(),
                row.getRateMultiplier());
    }
}
