package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.application.model.SpecialHandlingConfig;

import java.util.List;
import java.util.Optional;

/** Maintains the handling options, including retired ones. */
public interface SpecialHandlingAdminRepository {

    /** In display order. */
    List<SpecialHandlingConfig> findAll();

    Optional<SpecialHandlingConfig> findByCode(String code);

    SpecialHandlingConfig save(SpecialHandlingConfig option);
}
