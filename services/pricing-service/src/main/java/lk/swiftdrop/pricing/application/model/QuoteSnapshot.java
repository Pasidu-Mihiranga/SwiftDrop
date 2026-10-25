package lk.swiftdrop.pricing.application.model;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable quote representation persisted and returned without recalculation. */
public record QuoteSnapshot(UUID quoteId, Money fee, List<FeeBreakdown.Line> breakdown,
                            String fromZone, String toZone, String fromDistrict, String toDistrict,
                            BigDecimal weightKg, SizeClassCode sizeClass, ServiceLevel serviceLevel,
                            BigDecimal codAmount, Instant issuedAt, Instant expiresAt) {

    public QuoteSnapshot {
        breakdown = List.copyOf(breakdown);
    }

    public record Money(BigDecimal amount, String currency) { }
}
