package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;

import java.util.List;

/** Runs several strategies in order, each adding its line to the breakdown built so far. */
public final class CompositePricingStrategy implements PricingStrategy {

    private final List<PricingStrategy> strategies;

    public CompositePricingStrategy(PricingStrategy... strategies) {
        this.strategies = List.of(strategies);
    }

    @Override
    public FeeBreakdown price(PricingContext context, FeeBreakdown soFar) {
        FeeBreakdown result = soFar;
        for (PricingStrategy strategy : strategies) {
            result = strategy.price(context, result);
        }
        return result;
    }
}
