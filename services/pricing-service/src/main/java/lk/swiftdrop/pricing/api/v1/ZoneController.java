package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.pricing.api.dto.ZoneResponse;
import lk.swiftdrop.pricing.api.mapper.ZoneApiMapper;
import lk.swiftdrop.pricing.application.query.ZoneQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public service coverage. The web app loads its district options from here so that the zone
 * mapping lives in the pricing database and is never duplicated in the browser.
 */
@RestController
@RequestMapping("/api/v1/pricing/zones")
@Tag(name = "zones", description = "Public service-zone coverage")
public class ZoneController {

    private final ZoneQueryService zones;

    public ZoneController(ZoneQueryService zones) {
        this.zones = zones;
    }

    @GetMapping
    @Operation(summary = "Service zones and their districts (public)")
    public List<ZoneResponse> activeZones() {
        return zones.activeCoverage().stream().map(ZoneApiMapper::toPublicResponse).toList();
    }
}
