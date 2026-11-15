package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Maintains which districts cost extra to serve.
 *
 * <p>A charge can only be added after the newest period for its district. Publishing on the
 * same day would close a row to its own start date, and publishing into the past would leave
 * two open periods. Both are refused, so a mistake is corrected with a later date and the row
 * that was wrong stays on record.
 */
@Service
public class RemoteAreaAdminService {

    private final RemoteAreaSurchargeRepository remoteAreas;
    private final DistrictAssignmentRepository districts;

    public RemoteAreaAdminService(RemoteAreaSurchargeRepository remoteAreas,
                                  DistrictAssignmentRepository districts) {
        this.remoteAreas = remoteAreas;
        this.districts = districts;
    }

    @Transactional(readOnly = true)
    public List<RemoteAreaSurcharge> list() {
        return remoteAreas.findAll();
    }

    @Transactional
    public RemoteAreaSurcharge publish(String district, BigDecimal flatFee, LocalDate effectiveFrom) {
        String canonical = districts.findByDistrict(district.strip())
                .orElseThrow(() -> ResourceNotFoundException.of("District", district))
                .district();
        if (remoteAreas.hasPeriodStartingOnOrAfter(canonical, effectiveFrom)) {
            throw new ConflictException("REMOTE_AREA_PERIOD_CONFLICT",
                    "A charge for " + canonical + " already starts on or after " + effectiveFrom
                            + ". Publish from a later date.");
        }
        return remoteAreas.publish(canonical, flatFee, effectiveFrom);
    }
}
