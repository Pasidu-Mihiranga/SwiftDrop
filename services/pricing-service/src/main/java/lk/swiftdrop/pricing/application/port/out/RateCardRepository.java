package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes rate cards for ordered zone pairs. */
public interface RateCardRepository {

    /**
     * Every card for the pair that is in force on the given date, newest start date first.
     *
     * <p>The caller selects the latest start date and rejects ties rather than silently picking one.
     */
    List<RateCard> findInForce(ZoneCode fromZone, ZoneCode toZone, LocalDate on);

    List<RateCard> findAll();

    Optional<RateCard> findById(UUID id);

    RateCard save(RateCard rateCard);
}
