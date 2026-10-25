package lk.swiftdrop.pricing.application.model;

import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.time.LocalDate;

/**
 * Optional narrowing for a rate-card listing. A null field means "do not filter on this".
 *
 * @param activeOn keep only cards in force on this date
 */
public record RateCardFilter(ZoneCode fromZone, ZoneCode toZone, LocalDate activeOn) {

    public static RateCardFilter unfiltered() {
        return new RateCardFilter(null, null, null);
    }
}
