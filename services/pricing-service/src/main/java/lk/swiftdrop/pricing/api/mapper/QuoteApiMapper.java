package lk.swiftdrop.pricing.api.mapper;

import lk.swiftdrop.pricing.api.dto.QuoteResponse;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;

import java.util.List;

/** Turns a stored quote snapshot into the wire shape published in the OpenAPI contract. */
public final class QuoteApiMapper {

    private QuoteApiMapper() {
    }

    public static QuoteResponse toResponse(QuoteSnapshot snapshot) {
        List<QuoteResponse.BreakdownLineResponse> lines = snapshot.breakdown().stream()
                .map(line -> new QuoteResponse.BreakdownLineResponse(line.rule(), line.amount()))
                .toList();
        return new QuoteResponse(snapshot.quoteId(),
                new QuoteResponse.Money(snapshot.fee().amount(), snapshot.fee().currency()),
                lines, snapshot.fromZone(), snapshot.toZone(), snapshot.fromDistrict(),
                snapshot.toDistrict(), snapshot.weightKg(), snapshot.sizeClass(),
                snapshot.serviceLevel(), snapshot.codAmount(), snapshot.issuedAt(),
                snapshot.expiresAt(), snapshot.chargeableWeightKg(), snapshot.volumetricWeightKg(),
                snapshot.weightBasis(),
                // Sent only when it differs, so the customer is told their selection was
                // changed and is not left to compare two identical fields.
                snapshot.sizeClassWasDerived() ? snapshot.declaredSizeClass() : null,
                snapshot.declaredValue(), snapshot.agreementId());
    }
}
