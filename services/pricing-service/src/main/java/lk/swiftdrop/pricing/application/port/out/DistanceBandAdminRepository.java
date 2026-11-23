package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.domain.model.DistanceBand;

import java.time.LocalDate;
import java.util.List;

/** Maintains the versioned line-haul bands, which are always replaced as a complete set. */
public interface DistanceBandAdminRepository {

    /** Every band ever published, newest set first and each set by distance. */
    List<Versioned<DistanceBand>> findAllBandVersions();

    boolean hasBandVersionStartingOnOrAfter(LocalDate day);

    /** Closes every band in force on {@code effectiveFrom} and adds the new set from that day. */
    List<Versioned<DistanceBand>> publishBands(List<DistanceBand> bands, LocalDate effectiveFrom);
}
