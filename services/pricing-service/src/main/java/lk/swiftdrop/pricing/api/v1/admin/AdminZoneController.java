package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.pricing.api.dto.DistrictAssignmentResponse;
import lk.swiftdrop.pricing.api.dto.ZoneResponse;
import lk.swiftdrop.pricing.api.mapper.ZoneApiMapper;
import lk.swiftdrop.pricing.application.query.ZoneQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of the fixed zones and district mapping (ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-zones", description = "Fixed zones and district mapping (ADMIN, read-only)")
public class AdminZoneController {

    private final ZoneQueryService queries;

    public AdminZoneController(ZoneQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/zones")
    @Operation(summary = "List zones including deactivated ones (ADMIN)")
    public List<ZoneResponse> listZones() {
        return queries.allZones().stream().map(ZoneApiMapper::toAdminResponse).toList();
    }

    @GetMapping("/districts")
    @Operation(summary = "List every district and the zone it belongs to (ADMIN)")
    public List<DistrictAssignmentResponse> listDistricts() {
        return queries.allDistrictAssignments().stream().map(ZoneApiMapper::toResponse).toList();
    }
}
