package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.WeightTier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read side of rate-card administration. */
@Service
@Transactional(readOnly = true)
public class RateCardQueryService {

    private final RateCardRepository rateCards;
    private final WeightTierRepository weightTiers;

    public RateCardQueryService(RateCardRepository rateCards, WeightTierRepository weightTiers) {
        this.rateCards = rateCards;
        this.weightTiers = weightTiers;
    }

    public Paged<RateCard> list(RateCardFilter filter, int page, int size) {
        return rateCards.findPage(filter, page, size);
    }

    public List<WeightTier> tiersOf(UUID rateCardId) {
        return weightTiers.findByRateCard(rateCardId);
    }

    /** Read together so that a page of cards costs one query rather than one per card. */
    public Map<UUID, List<WeightTier>> tiersOf(Collection<UUID> rateCardIds) {
        return weightTiers.findByRateCards(rateCardIds);
    }

    public RateCard find(UUID rateCardId) {
        return rateCards.findById(rateCardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Rate card", rateCardId));
    }
}
