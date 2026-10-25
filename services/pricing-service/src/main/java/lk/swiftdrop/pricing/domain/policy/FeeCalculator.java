package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Assembles the pricing rules and runs them over one parcel.
 *
 * <p>The fee formula lives in the shape of the chain rather than in this class. Base and weight
 * are independent strategies; size, express and cash-on-delivery are decorators layered on top,
 * so each one charges against the rounded total of everything beneath it:
 *
 * <pre>
 *   COD( EXPRESS( SIZE( ZoneMatrix + Weight ) ) )
 * </pre>
 *
 * <p>Adding a rule means adding a decorator, not editing a calculation. The chain is stateless
 * and holds no parcel data, so one calculator can serve every request.
 */
public final class FeeCalculator {

    private final PricingStrategy pipeline;

    public FeeCalculator() {
        this(new CodFeeDecorator(
                new ExpressSurchargeDecorator(
                        new SizeSurchargeDecorator(
                                new CompositePricingStrategy(new ZoneMatrixStrategy(), new WeightStrategy())))));
    }

    /** Lets a test or a future caller price with a different set of rules. */
    public FeeCalculator(PricingStrategy pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    public FeeBreakdown calculate(RateCard card, SizeClassRate sizeClass, BigDecimal weightKg,
                                  ServiceLevel serviceLevel, BigDecimal codAmount) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(sizeClass, "sizeClass");
        Objects.requireNonNull(serviceLevel, "serviceLevel");
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        if (!sizeClass.accepts(weightKg)) {
            throw new IllegalArgumentException("Weight exceeds the size class limit");
        }
        if (codAmount == null || codAmount.signum() < 0) {
            throw new IllegalArgumentException("COD amount must not be negative");
        }

        PricingContext context = new PricingContext(card, sizeClass, weightKg, serviceLevel, codAmount);
        return pipeline.price(context, FeeBreakdown.empty());
    }
}
