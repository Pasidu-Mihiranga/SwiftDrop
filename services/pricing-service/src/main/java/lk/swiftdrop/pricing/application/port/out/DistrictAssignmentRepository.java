package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

import java.util.List;
import java.util.Optional;

/** Reads and writes the district-to-zone mapping. */
public interface DistrictAssignmentRepository {

    List<DistrictAssignment> findAll();

    Optional<DistrictAssignment> findByDistrict(String district);

    /** Resolves a district to the zone that serves it, ignoring deactivated zones. */
    Optional<ZoneCode> findActiveZoneCodeByDistrict(String district);

    /**
     * Assigns the district to the zone, moving it if it was already mapped elsewhere.
     *
     * @return the new assignment, or empty if no zone carries that code
     */
    Optional<DistrictAssignment> assign(String district, ZoneCode zoneCode);
}
