package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** The rounded pricing lines and their exact sum, in LKR. */
public record FeeBreakdown(List<Line> lines, BigDecimal fee) {

    public FeeBreakdown {
        lines = List.copyOf(lines);
        Objects.requireNonNull(fee, "fee");
    }

    public record Line(PricingRule rule, BigDecimal amount) {
        public Line {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
