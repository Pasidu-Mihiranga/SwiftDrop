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
 *   TAX( COD( INSURANCE( MERCHANT_DISCOUNT( REMOTE_AREA( SPECIAL_HANDLING( FUEL(
 *       MINIMUM_CHARGE( EXPRESS( SIZE( LaneCharge + LineHaul + Weight ) ) ) ) ) ) ) ) ) )
 * </pre>
 *
 * <p>Read from the inside out: the transport charge (lane, line haul, weight, size, express) is
 * floored by the minimum, fuel is charged on that, the optional extras are added, and tax is
 * charged on the finished total. Every rule added after the first release contributes nothing
 * when its configuration is absent, so a context without it prices exactly as before.
 *
 * <p>Two of those do not compound, and their position in the chain affects only the order of
 * the lines: the cash-on-delivery commission is a fraction of the cash being collected, and the
 * insurance premium comes from the declared value. Both are stated here because a reviewer
 * reading the nesting would reasonably assume otherwise.
 *
 * <p>Adding a rule means adding a decorator, not editing a calculation. The chain is stateless
 * and holds no parcel data, so one calculator can serve every request. It is a plain object so
 * the domain stays free of Spring; {@code PricingPolicyConfiguration} publishes it as a bean.
 */
public final class FeeCalculator {

    private final PricingStrategy pipeline;

    public FeeCalculator() {
        this(defaultPipeline());
    }

    /** Lets a test or a future caller price with a different set of rules. */
    public FeeCalculator(PricingStrategy pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    /** The rule chain as the contract documents it. Kept in one place so the order is reviewable. */
    public static PricingStrategy defaultPipeline() {
        // Built from the inside out, one rule per line, in the order PricingRule declares.
        PricingStrategy chain = new CompositePricingStrategy(
                new LaneChargeStrategy(), new LineHaulStrategy(), new TieredWeightStrategy());
        chain = new SizeSurchargeDecorator(chain);
        chain = new ExpressSurchargeDecorator(chain);
        chain = new MinimumChargeDecorator(chain);
        chain = new FuelSurchargeDecorator(chain);
        chain = new SpecialHandlingDecorator(chain);
        chain = new RemoteAreaSurchargeDecorator(chain);
        chain = new MerchantDiscountDecorator(chain);
        chain = new InsuranceSurchargeDecorator(chain);
        chain = new CodFeeDecorator(chain);
        return new TaxDecorator(chain);
    }

    /**
     * Prices one parcel against one set of rates.
     *
     * <p>This is the entry point the chain is built for. Validation lives here rather than in the
     * rules, so a rule only ever describes its own charge.
     */
    public FeeBreakdown calculate(PricingContext context) {
        Objects.requireNonNull(context, "context");
        PricingContext.Parcel parcel = context.parcel();
        if (parcel.actualWeightKg().signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        if (parcel.chargeableWeightKg().signum() <= 0) {
            throw new IllegalArgumentException("Chargeable weight must be positive");
        }
        if (!parcel.sizeClass().accepts(parcel.actualWeightKg())) {
            throw new IllegalArgumentException("Weight exceeds the size class limit");
        }
        if (parcel.codAmount().signum() < 0) {
            throw new IllegalArgumentException("COD amount must not be negative");
        }
        return pipeline.price(context, FeeBreakdown.empty());
    }

    /**
     * Prices a parcel whose billable weight is simply what it weighs.
     *
     * <p>Kept for callers that have no parcel dimensions to offer; it builds the context with
     * chargeable weight equal to actual weight, which is the same answer the service gave before
     * volumetric weight existed.
     */
    public FeeBreakdown calculate(RateCard card, SizeClassRate sizeClass, BigDecimal weightKg,
                                  ServiceLevel serviceLevel, BigDecimal codAmount) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(sizeClass, "sizeClass");
        Objects.requireNonNull(serviceLevel, "serviceLevel");
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        if (codAmount == null || codAmount.signum() < 0) {
            throw new IllegalArgumentException("COD amount must not be negative");
        }
        return calculate(new PricingContext(
                new PricingContext.Parcel(weightKg, weightKg, sizeClass, serviceLevel, codAmount),
                new PricingContext.Rates(card)));
    }
}
