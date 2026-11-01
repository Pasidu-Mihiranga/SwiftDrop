package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.InsuranceBand;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.WeightTier;

import java.math.BigDecimal;
import java.util.List;
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
                         SizeClassRate sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                         BigDecimal declaredValue) {

        /** A parcel sent without cover. */
        public Parcel(BigDecimal actualWeightKg, BigDecimal chargeableWeightKg, SizeClassRate sizeClass,
                      ServiceLevel serviceLevel, BigDecimal codAmount) {
            this(actualWeightKg, chargeableWeightKg, sizeClass, serviceLevel, codAmount, null);
        }

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
    public record Rates(RateCard card, List<WeightTier> weightTiers,
                        RemoteAreaSurcharge originRemoteArea, RemoteAreaSurcharge destinationRemoteArea,
                        List<InsuranceBand> insuranceBands) {

        public Rates {
            Objects.requireNonNull(card, "card");
            weightTiers = weightTiers == null ? List.of() : List.copyOf(weightTiers);
            insuranceBands = insuranceBands == null ? List.of() : List.copyOf(insuranceBands);
        }

        /** A lane priced by a single per-kilogram rate, with no surcharges configured. */
        public Rates(RateCard card) {
            this(card, List.of(), null, null, List.of());
        }

        /** A lane with weight bands and nothing else configured. */
        public Rates(RateCard card, List<WeightTier> weightTiers) {
            this(card, weightTiers, null, null, List.of());
        }
    }
}
