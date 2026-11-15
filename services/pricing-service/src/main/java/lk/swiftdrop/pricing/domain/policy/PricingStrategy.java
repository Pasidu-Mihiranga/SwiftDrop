package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;

/**
 * One way of pricing a parcel (Strategy).
 *
 * <p>Implementations either compute a charge of their own, such as
 * {@link ZoneMatrixStrategy} and {@link TieredWeightStrategy}, or wrap another strategy and layer a
 * surcharge on top of it, which is what the {@link PricingDecorator} subclasses do. Because the
 * interface is the same either way, the quote pipeline is assembled by composition rather than
 * by a fixed sequence of if-statements.
 */
public interface PricingStrategy {

    /**
     * Prices the parcel, adding this rule's line to whatever has already been charged.
     *
     * @param context  the parcel and the configuration being applied
     * @param soFar    the breakdown built by the rules that ran before this one
     * @return a new breakdown; the argument is never modified
     */
    FeeBreakdown price(PricingContext context, FeeBreakdown soFar);
}
