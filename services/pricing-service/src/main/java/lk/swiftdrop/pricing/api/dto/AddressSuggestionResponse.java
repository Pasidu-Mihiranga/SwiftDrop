package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.application.query.AddressSearchService.Suggestion;

import java.math.BigDecimal;

/** A searched place inside the service area and the district a quote will use for it. */
public record AddressSuggestionResponse(String label, BigDecimal latitude, BigDecimal longitude, String district,
                                        String zone) {

    public static AddressSuggestionResponse of(Suggestion suggestion) {
        return new AddressSuggestionResponse(suggestion.label(), suggestion.point().latitude(),
                suggestion.point().longitude(), suggestion.district(), suggestion.zone());
    }
}
