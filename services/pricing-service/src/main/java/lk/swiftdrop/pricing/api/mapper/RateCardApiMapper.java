package lk.swiftdrop.pricing.api.mapper;

import lk.swiftdrop.pricing.api.dto.PageMetaResponse;
import lk.swiftdrop.pricing.api.dto.RateCardPageResponse;
import lk.swiftdrop.pricing.api.dto.RateCardResponse;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.domain.model.RateCard;

/** Turns domain rate cards into the wire shapes published in the OpenAPI contract. */
public final class RateCardApiMapper {

    private RateCardApiMapper() {
    }

    public static RateCardResponse toResponse(RateCard card) {
        return new RateCardResponse(card.id(), card.fromZone().value(), card.toZone().value(),
                card.baseFee(), card.perKgFee(), card.expressMultiplier(), card.codFeePct(),
                card.effectiveFrom(), card.effectiveTo());
    }

    public static RateCardPageResponse toResponse(Paged<RateCard> page) {
        return new RateCardPageResponse(
                page.content().stream().map(RateCardApiMapper::toResponse).toList(),
                new PageMetaResponse(page.page(), page.size(), page.totalElements(), page.totalPages()));
    }
}
