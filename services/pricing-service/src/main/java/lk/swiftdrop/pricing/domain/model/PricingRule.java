package lk.swiftdrop.pricing.domain.model;

/**
 * Rule identifiers and their calculation order in the quote breakdown.
 *
 * <p>Declaration order is emission order, and the chain in {@code FeeCalculator} is built to
 * match. Keeping the two in step means the breakdown a customer reads always runs from the
 * charge for carrying the parcel down to the charges layered on top of it.
 *
 * <p>BASE and DISTANCE are alternatives, never both: a lane is priced either by zone or by
 * the road travelled, depending on the service level. LINE_HAUL is the distance-band charge
 * and applies only to the zone-priced side, since same-day work already pays per kilometre.
 */
public enum PricingRule {
    BASE, DISTANCE, LINE_HAUL, WEIGHT, SIZE, EXPRESS, MINIMUM_CHARGE, FUEL, SPECIAL_HANDLING,
    REMOTE_AREA, MERCHANT_DISCOUNT, INSURANCE, COD, TAX
}
