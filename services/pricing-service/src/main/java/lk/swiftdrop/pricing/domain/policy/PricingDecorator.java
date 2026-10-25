package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A surcharge layered on top of another strategy (Decorator).
 *
 * <p>The decorator runs the strategy it wraps first, then charges its own rule against the
 * rounded total that came back. That ordering is what makes the fee formula explicit in the
 * object graph: size is charged on base plus weight, and express on base plus weight plus size.
 */
public abstract class PricingDecorator implements PricingStrategy {

    private final PricingStrategy delegate;

    protected PricingDecorator(PricingStrategy delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public final FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        FeeBreakdown underlying = delegate.price(context, soFar);
        return underlying.plus(rule(), surcharge(context, underlying.fee()));
    }

    /** Which breakdown line this decorator contributes. */
    protected abstract PricingRule rule();

    /**
     * The charge to add.
     *
     * @param chargedSoFar the rounded total of every rule underneath this one
     */
    protected abstract BigDecimal surcharge(PricingContext context, BigDecimal chargedSoFar);
}
