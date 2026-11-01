package lk.swiftdrop.pricing.api.mapper;

import lk.swiftdrop.pricing.api.dto.PageMetaResponse;
import lk.swiftdrop.pricing.api.dto.RateCardPageResponse;
import lk.swiftdrop.pricing.api.dto.RateCardResponse;
import lk.swiftdrop.pricing.api.dto.WeightTierRequest;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.WeightTier;

import java.util.List;

/** Turns domain rate cards into the wire shapes published in the OpenAPI contract. */
public final class RateCardApiMapper {

    private RateCardApiMapper() {
    }

    public static RateCardResponse toResponse(RateCard card) {
        return toResponse(card, List.of());
    }

    public static RateCardResponse toResponse(RateCard card, List<WeightTier> tiers) {
        return new RateCardResponse(card.id(), card.fromZone().value(), card.toZone().value(),
                card.baseFee(), card.perKgFee(), card.expressMultiplier(), card.codFeePct(),
                card.effectiveFrom(), card.effectiveTo(),
                tiers.stream().map(t -> new WeightTierRequest(t.upToKg(), t.perKgFee())).toList());
    }

    public static RateCardPageResponse toResponse(Paged<RateCard> page) {
        return new RateCardPageResponse(
                page.content().stream().map(RateCardApiMapper::toResponse).toList(),
                new PageMetaResponse(page.page(), page.size(), page.totalElements(), page.totalPages()));
    }
}
