package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.DistrictLocator;
import lk.swiftdrop.pricing.application.port.out.SpecialHandlingRepository;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.SpecialHandlingOption;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Public reads the quote form needs before it can ask for a price. */
@Service
@Transactional(readOnly = true)
public class CoverageQueryService {

    private final DistrictLocator locator;
    private final DistrictAssignmentRepository districts;
    private final SpecialHandlingRepository specialHandling;

    public CoverageQueryService(DistrictLocator locator, DistrictAssignmentRepository districts,
                                SpecialHandlingRepository specialHandling) {
        this.locator = locator;
        this.districts = districts;
        this.specialHandling = specialHandling;
    }

    /**
     * The district a map point is in, and the zone serving it.
     *
     * @return empty when the point is outside every district, or in one no active zone serves
     */
    public Optional<LocatedPoint> locate(GeoPoint point) {
        Objects.requireNonNull(point, "point");
        return locator.districtAt(point).flatMap(district -> districts.findActiveZoneCodeByDistrict(district)
                .map(zone -> new LocatedPoint(district, zone)));
    }

    public List<SpecialHandlingOption> specialHandlingOptions() {
        return specialHandling.findActive();
    }

    public record LocatedPoint(String district, ZoneCode zone) { }
}
