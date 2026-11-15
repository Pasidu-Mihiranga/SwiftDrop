package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Chooses the size class a parcel actually belongs to.
 *
 * <p>While the class was whatever the customer selected, the size multiplier was theirs to
 * choose too: declaring a wardrobe as small cost nothing and saved the surcharge. Given real
 * measurements the service picks the cheapest class the parcel genuinely fits, by multiplier,
 * so the figure is derived from the parcel rather than asserted about it.
 *
 * <p>Without dimensions there is nothing to check and the declared class stands.
 */
public final class SizeClassSelector {

    private SizeClassSelector() {
    }

    /**
     * The cheapest class whose weight and geometry limits the parcel satisfies.
     *
     * @return empty when the parcel fits no configured class
     */
    public static Optional<SizeClassRate> select(Collection<SizeClassRate> classes,
                                                 BigDecimal weightKg, Dimensions dimensions) {
        if (classes == null || dimensions == null) {
            return Optional.empty();
        }
        return classes.stream()
                .filter(rate -> rate.accepts(weightKg))
                .filter(rate -> rate.acceptsDimensions(dimensions))
                .min(Comparator.comparing(SizeClassRate::multiplier)
                        .thenComparing(rate -> rate.code().ordinal()));
    }
}
