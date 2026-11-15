package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.util.List;
import java.util.Optional;

/** Reads the fixed zones together with their district coverage. */
public interface ZoneRepository {

    /** Active zones only, as advertised by the public coverage endpoint. */
    List<Zone> findActive();

    /** Every zone, including deactivated ones, for administration. */
    List<Zone> findAll();

    Optional<Zone> findByCode(ZoneCode code);

    boolean existsByCode(ZoneCode code);
}
