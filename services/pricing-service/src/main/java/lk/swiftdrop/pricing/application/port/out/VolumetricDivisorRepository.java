package lk.swiftdrop.pricing.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Reads the volumetric divisor in force on a given date. */
public interface VolumetricDivisorRepository {

    /**
     * The divisor applying on that date, or empty when none is configured.
     *
     * <p>Empty is a valid state, not an error: with no divisor the service charges a parcel on
     * what it weighs, which is what it did before volumetric weight existed.
     */
    Optional<BigDecimal> findInForce(LocalDate on);
}
