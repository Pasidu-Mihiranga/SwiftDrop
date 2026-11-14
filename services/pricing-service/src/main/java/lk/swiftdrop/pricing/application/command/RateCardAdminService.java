package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.WeightTier;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rate-card maintenance. Restricted to administrators at the API boundary.
 *
 * <p>Cards are versioned, never edited in place: a price change closes the card in force and adds
 * a new one. That is what lets an issued quote keep the fee it was given, because the quote holds
 * its own stored snapshot and the card it was priced from is still on record.
 */
@Service
@Transactional
public class RateCardAdminService {

    private final RateCardRepository rateCards;
    private final ZoneRepository zones;
    private final WeightTierRepository weightTiers;

    public RateCardAdminService(RateCardRepository rateCards, ZoneRepository zones,
                                WeightTierRepository weightTiers) {
        this.rateCards = rateCards;
        this.zones = zones;
        this.weightTiers = weightTiers;
    }

    /**
     * Publishes a new card, optionally with weight bands.
     *
     * <p>Bands are written with the card rather than through an endpoint of their own, because
     * a card is never edited: changing how a lane charges for weight means closing it and
     * publishing a replacement, exactly as changing its base fee does.
     */
    public RateCard create(ZoneCode fromZone, ZoneCode toZone, BigDecimal baseFee, BigDecimal perKgFee,
                           BigDecimal expressMultiplier, BigDecimal codFeePct,
                           LocalDate effectiveFrom, LocalDate effectiveTo, List<WeightTier> tiers) {
        requireZone(fromZone);
        if (!fromZone.equals(toZone)) {
            requireZone(toZone);
        }
        RateCard candidate = buildCard(fromZone, toZone, baseFee, perKgFee, expressMultiplier, codFeePct,
                effectiveFrom, effectiveTo);
        rejectOverlap(candidate);
        RateCard saved = rateCards.save(candidate);
        weightTiers.setForRateCard(saved.id(), tiers == null ? List.of() : tiers);
        return saved;
    }

    /** A lane charging a single per-kilogram rate. */
    public RateCard create(ZoneCode fromZone, ZoneCode toZone, BigDecimal baseFee, BigDecimal perKgFee,
                           BigDecimal expressMultiplier, BigDecimal codFeePct,
                           LocalDate effectiveFrom, LocalDate effectiveTo) {
        return create(fromZone, toZone, baseFee, perKgFee, expressMultiplier, codFeePct,
                effectiveFrom, effectiveTo, List.of());
    }

    /** Ends a card's period so it stops applying to new quotes from {@code effectiveTo} onwards. */
    public RateCard close(UUID rateCardId, LocalDate effectiveTo) {
        RateCard existing = rateCards.findById(rateCardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Rate card", rateCardId));
        if (effectiveTo == null || !effectiveTo.isAfter(existing.effectiveFrom())) {
            throw new BusinessRuleViolationException("INVALID_EFFECTIVE_RANGE",
                    "A rate card must end after " + existing.effectiveFrom());
        }
        return rateCards.save(existing.closedOn(effectiveTo));
    }

    private void requireZone(ZoneCode code) {
        if (!zones.existsByCode(code)) {
            throw ResourceNotFoundException.of("Zone", code);
        }
    }

    /** Turns the domain model's own argument checks into a 422 rather than a 500. */
    private static RateCard buildCard(ZoneCode fromZone, ZoneCode toZone, BigDecimal baseFee, BigDecimal perKgFee,
                                      BigDecimal expressMultiplier, BigDecimal codFeePct,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        try {
            return new RateCard(null, fromZone, toZone, baseFee, perKgFee, expressMultiplier, codFeePct,
                    effectiveFrom, effectiveTo);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleViolationException("INVALID_RATE_CARD", invalid.getMessage());
        }
    }

    /**
     * Two cards for the same lane may not both be in force at once, otherwise quoting would have
     * to pick one arbitrarily. Periods are half-open, so one card ending the day another starts
     * is allowed.
     */
    private void rejectOverlap(RateCard candidate) {
        List<RateCard> existing = rateCards.findByPair(candidate.fromZone(), candidate.toZone());
        for (RateCard other : existing) {
            if (overlaps(candidate, other)) {
                throw new ConflictException("RATE_CARD_OVERLAP",
                        "A rate card for " + candidate.fromZone() + " to " + candidate.toZone()
                                + " is already in force from " + other.effectiveFrom());
            }
        }
    }

    private static boolean overlaps(RateCard left, RateCard right) {
        boolean leftStartsBeforeRightEnds = right.effectiveTo() == null
                || left.effectiveFrom().isBefore(right.effectiveTo());
        boolean rightStartsBeforeLeftEnds = left.effectiveTo() == null
                || right.effectiveFrom().isBefore(left.effectiveTo());
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds;
    }
}
