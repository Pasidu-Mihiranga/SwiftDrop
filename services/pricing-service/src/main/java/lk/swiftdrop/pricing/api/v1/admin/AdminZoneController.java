package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.DistrictAssignmentRequest;
import lk.swiftdrop.pricing.api.dto.DistrictAssignmentResponse;
import lk.swiftdrop.pricing.api.dto.ZoneCreateRequest;
import lk.swiftdrop.pricing.api.dto.ZoneResponse;
import lk.swiftdrop.pricing.api.dto.ZoneUpdateRequest;
import lk.swiftdrop.pricing.api.mapper.ZoneApiMapper;
import lk.swiftdrop.pricing.application.command.ZoneAdminService;
import lk.swiftdrop.pricing.application.query.ZoneQueryService;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Zone and district maintenance. Pricing configuration is administrator-only. */
@RestController
@RequestMapping("/api/v1/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-zones", description = "Zone and district administration (ADMIN)")
public class AdminZoneController {

    private final ZoneQueryService queries;
    private final ZoneAdminService commands;

    public AdminZoneController(ZoneQueryService queries, ZoneAdminService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/zones")
    @Operation(summary = "List zones including deactivated ones (ADMIN)")
    public List<ZoneResponse> listZones() {
        return queries.allZones().stream().map(ZoneApiMapper::toAdminResponse).toList();
    }

    @GetMapping("/zones/{zoneId}")
    @Operation(summary = "Get one zone (ADMIN)")
    public ZoneResponse getZone(@PathVariable UUID zoneId) {
        return ZoneApiMapper.toAdminResponse(commands.findZone(zoneId));
    }

    @PostMapping("/zones")
    @Operation(summary = "Create zone (ADMIN)")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneCreateRequest request) {
        Zone created = commands.createZone(ZoneCode.of(request.code()), request.name(), request.activeOrDefault());
        return ResponseEntity
                .created(URI.create("/api/v1/admin/pricing/zones/" + created.id()))
                .body(ZoneApiMapper.toAdminResponse(created));
    }

    @PutMapping("/zones/{zoneId}")
    @Operation(summary = "Rename a zone or change its active flag (ADMIN). The code is immutable.")
    public ZoneResponse updateZone(@PathVariable UUID zoneId, @Valid @RequestBody ZoneUpdateRequest request) {
        return ZoneApiMapper.toAdminResponse(commands.updateZone(zoneId, request.name(), request.active()));
    }

    @GetMapping("/districts")
    @Operation(summary = "List every district and the zone it belongs to (ADMIN)")
    public List<DistrictAssignmentResponse> listDistricts() {
        return queries.allDistrictAssignments().stream().map(ZoneApiMapper::toResponse).toList();
    }

    @PutMapping("/districts/{district}")
    @Operation(summary = "Assign or reassign a district to a zone (ADMIN)")
    public DistrictAssignmentResponse assignDistrict(@PathVariable String district,
                                                     @Valid @RequestBody DistrictAssignmentRequest request) {
        return ZoneApiMapper.toResponse(commands.assignDistrict(district, ZoneCode.of(request.zoneCode())));
    }
}
