package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;

import java.time.LocalDate;
import java.util.Optional;

/** Reads the remote-area surcharge in force for a district. */
public interface RemoteAreaSurchargeRepository {

    /** Empty when the district is not marked remote, which is the normal case. */
    Optional<RemoteAreaSurcharge> findInForce(String district, LocalDate on);

    /** Every surcharge ever configured, newest first, so an administrator can see the history. */
    java.util.List<RemoteAreaSurcharge> findAll();

    /** Whether a period for this district already starts on or after a date. */
    boolean hasPeriodStartingOnOrAfter(String district, LocalDate date);

    /**
     * Marks a district remote from a date.
     *
     * <p>Closes whatever was in force the day before, so periods stay half-open and the old
     * row is kept rather than edited, exactly as a rate card is.
     */
    RemoteAreaSurcharge publish(String district, java.math.BigDecimal flatFee, LocalDate effectiveFrom);
}
