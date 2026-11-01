package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.WeightTier;

import java.util.List;
import java.util.UUID;

/** Reads the weight bands configured for a rate card. */
public interface WeightTierRepository {

    /**
     * The bands for one card, ordered by limit ascending with the open-ended band last.
     *
     * <p>An empty list is the normal case, not an error: a lane with no bands is priced by the
     * single per-kilogram rate on its card.
     */
    List<WeightTier> findByRateCard(UUID rateCardId);

    /**
     * Sets the bands for a newly created card.
     *
     * <p>Only ever called for a card that has just been inserted. A card already in force is
     * never edited, so changing bands means closing it and publishing a replacement, exactly
     * as changing a base fee does.
     */
    void setForRateCard(UUID rateCardId, List<WeightTier> tiers);
}
