package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes zones together with their district coverage. */
public interface ZoneRepository {

    /** Active zones only, as advertised by the public coverage endpoint. */
    List<Zone> findActive();

    /** Every zone, including deactivated ones, for administration. */
    List<Zone> findAll();

    Optional<Zone> findById(UUID id);

    Optional<Zone> findByCode(ZoneCode code);

    boolean existsByCode(ZoneCode code);

    /** Inserts a new zone or updates the name and active flag of an existing one. */
    Zone save(Zone zone);
}
