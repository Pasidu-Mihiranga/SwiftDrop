package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The quote as published in the OpenAPI contract.
 *
 * <p>Issuing a quote and looking one up return this same shape, so a caller validating a quote
 * sees exactly what the customer was shown. It is a separate type from the stored snapshot on
 * purpose: what is kept in Redis and what is published on the wire evolve for different reasons,
 * and the wire shape is the one other teams depend on.
 */
public record QuoteResponse(UUID quoteId, Money fee, List<BreakdownLineResponse> breakdown,
                            String fromZone, String toZone, String fromDistrict, String toDistrict,
                            BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                            BigDecimal codAmount, Instant issuedAt, Instant expiresAt) {

    /** An amount and its currency. Mirrors the shared Money schema in common.v1.yaml. */
    public record Money(BigDecimal amount, String currency) { }

    /** One rule's contribution to the fee. The lines always sum exactly to the total. */
    public record BreakdownLineResponse(PricingRule rule, BigDecimal amount) { }
}
