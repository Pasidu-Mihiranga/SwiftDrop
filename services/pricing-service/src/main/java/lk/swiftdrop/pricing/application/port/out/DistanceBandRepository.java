package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.DistanceBand;

import java.time.LocalDate;
import java.util.List;

/** Reads the line-haul distance bands in force on a date. */
public interface DistanceBandRepository {

    /** Ordered by where each band starts. Empty means line haul is not configured. */
    List<DistanceBand> findBandsInForce(LocalDate on);
}
