package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.util.List;

/** Translates between JPA entities and the framework-free domain model. */
final class PricingPersistenceMapper {

    private PricingPersistenceMapper() {
    }

    static Zone toDomain(ZoneEntity entity) {
        List<String> districts = entity.getDistricts().stream()
                .map(DistrictZoneEntity::getDistrict)
                .sorted()
                .toList();
        return new Zone(entity.getId(), ZoneCode.of(entity.getCode()), entity.getName(), entity.isActive(), districts);
    }

    static DistrictAssignment toDomain(DistrictZoneEntity entity) {
        ZoneEntity zone = entity.getZone();
        return new DistrictAssignment(entity.getDistrict(), ZoneCode.of(zone.getCode()), zone.getName());
    }

    static SizeClassRate toDomain(SizeClassEntity entity) {
        return new SizeClassRate(SizeClassCode.valueOf(entity.getCode()), entity.getMaxWeightKg(),
                entity.getMaxDimensionsCm(), entity.getMultiplier());
    }

    static RateCard toDomain(RateCardEntity entity) {
        return new RateCard(entity.getId(),
                ZoneCode.of(entity.getFromZone().getCode()),
                ZoneCode.of(entity.getToZone().getCode()),
                entity.getBaseFee(),
                entity.getPerKgFee(),
                entity.getExpressMultiplier(),
                entity.getCodFeePct(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo());
    }
}
