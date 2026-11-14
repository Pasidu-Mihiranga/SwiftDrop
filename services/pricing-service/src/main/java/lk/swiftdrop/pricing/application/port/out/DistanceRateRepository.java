package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.DistanceRate;

import java.time.LocalDate;
import java.util.Optional;

/** Reads the distance rate in force on a date. */
public interface DistanceRateRepository {

    Optional<DistanceRate> findInForce(LocalDate on);
}
