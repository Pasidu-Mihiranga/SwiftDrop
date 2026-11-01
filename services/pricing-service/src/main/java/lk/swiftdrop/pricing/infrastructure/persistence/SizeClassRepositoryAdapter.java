package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class SizeClassRepositoryAdapter implements SizeClassRepository {

    private final SizeClassJpaRepository sizeClasses;

    SizeClassRepositoryAdapter(SizeClassJpaRepository sizeClasses) {
        this.sizeClasses = sizeClasses;
    }

    @Override
    public List<SizeClassRate> findAll() {
        return sizeClasses.findAllByOrderByMultiplierAsc().stream()
                .map(PricingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<SizeClassRate> findByCode(SizeClassCode code) {
        return sizeClasses.findById(code.name()).map(PricingPersistenceMapper::toDomain);
    }

    @Override
    public SizeClassRate save(SizeClassRate rate) {
        SizeClassEntity entity = sizeClasses.findById(rate.code().name())
                .map(existing -> {
                    existing.reprice(rate.maxWeightKg(), rate.maxDimensionsLabel(),
                            rate.maxDimensions().lengthCm(), rate.maxDimensions().widthCm(),
                            rate.maxDimensions().heightCm(), rate.multiplier());
                    return existing;
                })
                .orElseGet(() -> new SizeClassEntity(rate.code().name(), rate.maxWeightKg(),
                        rate.maxDimensionsLabel(), rate.maxDimensions().lengthCm(),
                        rate.maxDimensions().widthCm(), rate.maxDimensions().heightCm(),
                        rate.multiplier()));
        return PricingPersistenceMapper.toDomain(sizeClasses.save(entity));
    }
}
