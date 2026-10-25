package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;

/** Everything a pricing rule may read. Rules never mutate it, so one context is safe to share. */
public record PricingContext(RateCard card, SizeClassRate sizeClass, BigDecimal weightKg,
                             ServiceLevel serviceLevel, BigDecimal codAmount) {
}
