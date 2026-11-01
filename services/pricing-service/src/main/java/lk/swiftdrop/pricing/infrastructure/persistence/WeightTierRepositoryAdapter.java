package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.WeightTier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** JPA adapter for a rate card's weight bands. */
@Repository
class WeightTierRepositoryAdapter implements WeightTierRepository {

    private final WeightTierJpaRepository tiers;

    WeightTierRepositoryAdapter(WeightTierJpaRepository tiers) {
        this.tiers = tiers;
    }

    @Override
    public void setForRateCard(UUID rateCardId, List<WeightTier> bands) {
        if (bands.isEmpty()) {
            return;
        }
        tiers.saveAll(bands.stream()
                .map(band -> new WeightTierEntity(UUID.randomUUID(), rateCardId, band.upToKg(), band.perKgFee()))
                .toList());
    }

    @Override
    public List<WeightTier> findByRateCard(UUID rateCardId) {
        return tiers.findByRateCardOrdered(rateCardId).stream()
                .map(entity -> new WeightTier(entity.getUpToKg(), entity.getPerKgFee()))
                .toList();
    }
}
