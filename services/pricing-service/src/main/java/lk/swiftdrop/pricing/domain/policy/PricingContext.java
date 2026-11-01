package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Everything a pricing rule may read. Rules never mutate it, so one context is safe to share.
 *
 * <p>It is split in two so that it can grow without becoming a record with a dozen positional
 * components: {@link Parcel} is what the customer is sending, {@link Rates} is the configuration
 * being applied to it. A rule reads one or both and charges its own line.
 */
public record PricingContext(Parcel parcel, Rates rates) {

    public PricingContext {
        Objects.requireNonNull(parcel, "parcel");
        Objects.requireNonNull(rates, "rates");
    }

    /**
     * The consignment being priced.
     *
     * @param actualWeightKg     what the parcel weighs on the scale
     * @param chargeableWeightKg what it is billed as; the greater of actual and volumetric weight,
     *                           and equal to {@code actualWeightKg} when no dimensions were given
     */
    public record Parcel(BigDecimal actualWeightKg, BigDecimal chargeableWeightKg,
                         SizeClassRate sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount) {

        public Parcel {
            Objects.requireNonNull(actualWeightKg, "actualWeightKg");
            Objects.requireNonNull(chargeableWeightKg, "chargeableWeightKg");
            Objects.requireNonNull(sizeClass, "sizeClass");
            Objects.requireNonNull(serviceLevel, "serviceLevel");
            Objects.requireNonNull(codAmount, "codAmount");
        }
    }

    /**
     * The configuration a rule prices against.
     *
     * <p>A component that is null means the rule which reads it does not apply to this quote.
     * No rule needs to branch on that beyond its own concern, because {@code FeeBreakdown.plus}
     * drops a zero line rather than reporting it.
     */
    public record Rates(RateCard card) {

        public Rates {
            Objects.requireNonNull(card, "card");
        }
    }
}
