package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.api.dto.LocationResponse;
import lk.swiftdrop.pricing.api.dto.SpecialHandlingResponse;
import lk.swiftdrop.pricing.application.query.CoverageQueryService;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public reads for the quote form: the handling options on offer, and which district a point on
 * the map is in. Both are anonymous, like the quote itself, because a visitor prices a parcel
 * before signing up.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@Tag(name = "zones", description = "Public service-zone coverage")
public class CoverageController {

    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final CoverageQueryService coverage;

    public CoverageController(CoverageQueryService coverage) {
        this.coverage = coverage;
    }

    @GetMapping("/special-handling")
    @Operation(summary = "Special handling options a quote can include (public)")
    public List<SpecialHandlingResponse> specialHandling() {
        return coverage.specialHandlingOptions().stream()
                .map(option -> new SpecialHandlingResponse(option.code(), option.label()))
                .toList();
    }

    /**
     * Resolved from district boundaries held by this service, so the answer is the same one the
     * quote endpoint will check the point against.
     */
    @GetMapping("/locate")
    @Operation(summary = "The district and zone a map point falls in (public)")
    public LocationResponse locate(@RequestParam BigDecimal latitude, @RequestParam BigDecimal longitude) {
        if (latitude.abs().compareTo(MAX_LATITUDE) > 0 || longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
            throw new BadRequestException("INVALID_COORDINATES", "Latitude or longitude is out of range");
        }
        return coverage.locate(new GeoPoint(latitude, longitude))
                .map(located -> new LocationResponse(located.district(), located.zone().value()))
                .orElseThrow(() -> new BusinessRuleViolationException("OUTSIDE_COVERAGE",
                        "That location is outside every district SwiftDrop serves"));
    }
}
