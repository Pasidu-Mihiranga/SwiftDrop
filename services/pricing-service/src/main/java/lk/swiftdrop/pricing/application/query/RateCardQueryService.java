package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read side of rate-card administration. */
@Service
@Transactional(readOnly = true)
public class RateCardQueryService {

    private final RateCardRepository rateCards;

    public RateCardQueryService(RateCardRepository rateCards) {
        this.rateCards = rateCards;
    }

    public Paged<RateCard> list(RateCardFilter filter, int page, int size) {
        return rateCards.findPage(filter, page, size);
    }

    public RateCard find(UUID rateCardId) {
        return rateCards.findById(rateCardId)
                .orElseThrow(() -> ResourceNotFoundException.of("Rate card", rateCardId));
    }
}
