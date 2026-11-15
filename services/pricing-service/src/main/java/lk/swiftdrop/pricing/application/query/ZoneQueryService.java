package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of zone coverage: what the public site and the admin screens display. */
@Service
@Transactional(readOnly = true)
public class ZoneQueryService {

    private final ZoneRepository zones;
    private final DistrictAssignmentRepository districts;

    public ZoneQueryService(ZoneRepository zones, DistrictAssignmentRepository districts) {
        this.zones = zones;
        this.districts = districts;
    }

    /** Coverage advertised publicly: active zones and the districts they serve. */
    public List<Zone> activeCoverage() {
        return zones.findActive();
    }

    /** Every zone, including deactivated ones. */
    public List<Zone> allZones() {
        return zones.findAll();
    }

    public List<DistrictAssignment> allDistrictAssignments() {
        return districts.findAll();
    }
}
