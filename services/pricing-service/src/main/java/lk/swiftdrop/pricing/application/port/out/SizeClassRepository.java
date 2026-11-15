package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.util.List;
import java.util.Optional;

/** Reads and writes the configured size-class limits and multipliers. */
public interface SizeClassRepository {

    List<SizeClassRate> findAll();

    Optional<SizeClassRate> findByCode(SizeClassCode code);

    SizeClassRate save(SizeClassRate rate);
}
