package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.WeightTier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                .map(WeightTierRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, List<WeightTier>> findByRateCards(Collection<UUID> rateCardIds) {
        Map<UUID, List<WeightTier>> bands = new LinkedHashMap<>();
        if (rateCardIds.isEmpty()) {
            return bands;
        }
        for (WeightTierEntity entity : tiers.findByRateCardsOrdered(rateCardIds)) {
            bands.computeIfAbsent(entity.getRateCardId(), id -> new ArrayList<>()).add(toDomain(entity));
        }
        return bands;
    }

    private static WeightTier toDomain(WeightTierEntity entity) {
        return new WeightTier(entity.getUpToKg(), entity.getPerKgFee());
    }
}
