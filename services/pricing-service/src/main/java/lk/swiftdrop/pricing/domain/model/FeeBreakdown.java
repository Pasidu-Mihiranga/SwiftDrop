package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The rounded pricing lines and their exact sum, in LKR. */
public record FeeBreakdown(List<Line> lines, BigDecimal fee) {

    public FeeBreakdown {
        lines = List.copyOf(lines);
        Objects.requireNonNull(fee, "fee");
    }

    /** The starting point of a pricing chain: nothing charged yet. */
    public static FeeBreakdown empty() {
        return new FeeBreakdown(List.of(), MonetaryRounding.zero());
    }

    /**
     * Adds one rule's charge, rounded to the monetary scale.
     *
     * <p>A rule that works out to nothing is left out of the breakdown rather than shown as a
     * zero line, but the running total is still the exact sum of the lines that remain.
     */
    public FeeBreakdown plus(PricingRule rule, BigDecimal rawAmount) {
        BigDecimal amount = MonetaryRounding.round(rawAmount);
        if (amount.signum() == 0) {
            return this;
        }
        List<Line> combined = new ArrayList<>(lines);
        combined.add(new Line(rule, amount));
        return new FeeBreakdown(combined, fee.add(amount));
    }

    public record Line(PricingRule rule, BigDecimal amount) {
        public Line {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
