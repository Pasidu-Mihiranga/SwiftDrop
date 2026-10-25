package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Zone and district maintenance. Restricted to administrators at the API boundary.
 *
 * <p>Zone codes are immutable, because rate cards and district assignments are keyed on the
 * zone's identity and renaming a code would silently orphan them.
 */
@Service
@Transactional
public class ZoneAdminService {

    private final ZoneRepository zones;
    private final DistrictAssignmentRepository districts;

    public ZoneAdminService(ZoneRepository zones, DistrictAssignmentRepository districts) {
        this.zones = zones;
        this.districts = districts;
    }

    public Zone createZone(ZoneCode code, String name, boolean active) {
        if (zones.existsByCode(code)) {
            throw new ConflictException("ZONE_CODE_TAKEN", "A zone with code " + code + " already exists");
        }
        return zones.save(Zone.newZone(code, name, active));
    }

    public Zone findZone(UUID zoneId) {
        return zones.findById(zoneId).orElseThrow(() -> ResourceNotFoundException.of("Zone", zoneId));
    }

    /**
     * Renames a zone or changes its active flag. Deactivating a zone that still has districts is
     * rejected, because those districts would become unquotable without anyone being told.
     */
    public Zone updateZone(UUID zoneId, String name, boolean active) {
        Zone existing = findZone(zoneId);
        if (!active && existing.hasDistricts()) {
            throw new BusinessRuleViolationException("ZONE_IN_USE",
                    "Zone " + existing.code() + " still has districts assigned and cannot be deactivated");
        }
        return zones.save(existing.with(name, active));
    }

    /** Assigns a district to a zone, moving it if it was already mapped elsewhere. */
    public DistrictAssignment assignDistrict(String district, ZoneCode zoneCode) {
        return districts.assign(district, zoneCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Zone", zoneCode));
    }
}
