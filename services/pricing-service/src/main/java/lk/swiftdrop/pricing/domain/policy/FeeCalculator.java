package lk.swiftdrop.pricing.domain.policy;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Applies the agreed pricing rules in order using the rounded amount from each preceding rule. */
public final class FeeCalculator {

    private static final int MONEY_SCALE = 2;
    private static final List<Rule> RULES = List.of(
            new Rule(PricingRule.BASE, (input, amounts) -> input.card().baseFee()),
            new Rule(PricingRule.WEIGHT, (input, amounts) -> input.card().perKgFee()
                    .multiply(input.weightKg().setScale(0, RoundingMode.CEILING))),
            new Rule(PricingRule.SIZE, (input, amounts) -> subtotal(amounts)
                    .multiply(input.sizeClass().multiplier().subtract(BigDecimal.ONE))),
            new Rule(PricingRule.EXPRESS, (input, amounts) -> input.serviceLevel() == ServiceLevel.EXPRESS
                    ? subtotal(amounts).add(amounts.get(PricingRule.SIZE))
                            .multiply(input.card().expressMultiplier().subtract(BigDecimal.ONE))
                    : BigDecimal.ZERO),
            new Rule(PricingRule.COD, (input, amounts) -> input.codAmount()
                    .multiply(input.card().codFeePct())));

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

        Input input = new Input(card, sizeClass, weightKg, serviceLevel, codAmount);
        EnumMap<PricingRule, BigDecimal> amounts = new EnumMap<>(PricingRule.class);
        List<FeeBreakdown.Line> lines = new ArrayList<>();
        BigDecimal fee = BigDecimal.ZERO.setScale(MONEY_SCALE);
        for (Rule rule : RULES) {
            BigDecimal amount = rule.calculate().apply(input, amounts).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            amounts.put(rule.code(), amount);
            if (amount.signum() != 0) {
                lines.add(new FeeBreakdown.Line(rule.code(), amount));
                fee = fee.add(amount);
            }
        }
        return new FeeBreakdown(lines, fee);
    }

    private static BigDecimal subtotal(EnumMap<PricingRule, BigDecimal> amounts) {
        return amounts.get(PricingRule.BASE).add(amounts.get(PricingRule.WEIGHT));
    }

    private record Input(RateCard card, SizeClassRate sizeClass, BigDecimal weightKg,
                         ServiceLevel serviceLevel, BigDecimal codAmount) { }

    private record Rule(PricingRule code,
                        BiFunction<Input, EnumMap<PricingRule, BigDecimal>, BigDecimal> calculate) { }
}
