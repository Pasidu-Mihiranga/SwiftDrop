package lk.swiftdrop.pricing.api.dto;

import java.util.List;

public record RateCardPageResponse(List<RateCardResponse> content, PageMetaResponse page) {
}
