package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects the rate card governing a quote for an ordered pair of active zones. */
@Service
@Transactional(readOnly = true)
public class RateCardSelectionService {

    private final ZoneRepository zones;
    private final RateCardRepository rateCards;

    public RateCardSelectionService(ZoneRepository zones, RateCardRepository rateCards) {
        this.zones = zones;
        this.rateCards = rateCards;
    }

    public RateCard select(ZoneCode fromZone, ZoneCode toZone, LocalDate quoteDate) {
        Objects.requireNonNull(fromZone, "fromZone");
        Objects.requireNonNull(toZone, "toZone");
        Objects.requireNonNull(quoteDate, "quoteDate");

        requireActive(fromZone);
        if (!fromZone.equals(toZone)) {
            requireActive(toZone);
        }

        List<RateCard> inForce = rateCards.findInForce(fromZone, toZone, quoteDate);
        RateCard newest = inForce.stream()
                .max(Comparator.comparing(RateCard::effectiveFrom))
                .orElseThrow(() -> new BusinessRuleViolationException("NO_RATE_CARD",
                        "No rate card is in force for " + fromZone + " to " + toZone));

        long latestCount = inForce.stream()
                .filter(card -> card.effectiveFrom().equals(newest.effectiveFrom()))
                .count();
        if (latestCount > 1) {
            throw new BusinessRuleViolationException("AMBIGUOUS_RATE_CARD",
                    "Multiple rate cards are in force for " + fromZone + " to " + toZone);
        }
        return newest;
    }

    private void requireActive(ZoneCode code) {
        if (zones.findByCode(code).filter(Zone::active).isEmpty()) {
            throw new BusinessRuleViolationException("DISTRICT_NOT_SERVICED",
                    "Zone " + code + " is not active or does not exist");
        }
    }
}
