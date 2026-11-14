package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.InsuranceBand;

import java.time.LocalDate;
import java.util.List;

/** Reads the insurance bands in force on a date. */
public interface InsuranceBandRepository {

    /** Bands ordered by limit ascending, with the open-ended band last. */
    List<InsuranceBand> findInForce(LocalDate on);
}
