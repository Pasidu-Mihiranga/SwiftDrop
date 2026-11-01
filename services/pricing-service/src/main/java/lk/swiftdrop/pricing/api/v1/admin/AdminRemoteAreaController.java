package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.RemoteAreaRequest;
import lk.swiftdrop.pricing.api.dto.RemoteAreaResponse;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Which districts cost extra to serve.
 *
 * <p>Writing one closes whatever was in force and inserts a replacement, so the history stays
 * readable and a quote issued under the old charge can still be explained.
 */
@RestController
@RequestMapping("/api/v1/admin/pricing/remote-areas")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "admin-pricing", description = "Pricing configuration (ADMIN)")
public class AdminRemoteAreaController {

    private final RemoteAreaSurchargeRepository remoteAreas;

    public AdminRemoteAreaController(RemoteAreaSurchargeRepository remoteAreas) {
        this.remoteAreas = remoteAreas;
    }

    @GetMapping
    @Operation(summary = "List remote-area surcharges (ADMIN)")
    public List<RemoteAreaResponse> list() {
        return remoteAreas.findAll().stream().map(RemoteAreaResponse::of).toList();
    }

    @PutMapping("/{district}")
    @Operation(summary = "Mark a district hard to reach from a date (ADMIN)")
    public RemoteAreaResponse publish(@PathVariable String district,
                                      @Valid @RequestBody RemoteAreaRequest request) {
        return RemoteAreaResponse.of(
                remoteAreas.publish(district, request.flatFee(), request.effectiveFrom()));
    }
}
