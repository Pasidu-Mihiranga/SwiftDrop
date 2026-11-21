package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;

import java.util.List;

/** Reads the special handling options customers can currently choose. */
public interface SpecialHandlingRepository {

    /** Active options only, in display order. */
    List<SpecialHandlingOption> findActive();
}
