package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.DistanceBandResponse;
import lk.swiftdrop.pricing.api.dto.DistanceBandSetRequest;
import lk.swiftdrop.pricing.api.dto.PricingSettingsRequest;
import lk.swiftdrop.pricing.api.dto.PricingSettingsResponse;
import lk.swiftdrop.pricing.api.dto.SameDayVehicleResponse;
import lk.swiftdrop.pricing.api.dto.SameDayVehicleUpdateRequest;
import lk.swiftdrop.pricing.api.dto.SpecialHandlingConfigResponse;
import lk.swiftdrop.pricing.api.dto.SpecialHandlingCreateRequest;
import lk.swiftdrop.pricing.api.dto.SpecialHandlingUpdateRequest;
import lk.swiftdrop.pricing.application.command.PricingConfigurationAdminService;
import lk.swiftdrop.pricing.domain.model.VehicleType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The fee settings, line-haul bands, handling options and same-day vehicles (ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-pricing-settings", description = "Fee settings, distance bands, handling and vehicles (ADMIN)")
public class AdminPricingConfigurationController {

    private final PricingConfigurationAdminService configuration;

    public AdminPricingConfigurationController(PricingConfigurationAdminService configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/settings")
    @Operation(summary = "Every published version of the fee settings, newest first (ADMIN)")
    public List<PricingSettingsResponse> settings() {
        return configuration.settingsHistory().stream().map(PricingSettingsResponse::of).toList();
    }

    @PostMapping("/settings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish fee settings from a date (ADMIN)")
    public PricingSettingsResponse publishSettings(@Valid @RequestBody PricingSettingsRequest request) {
        return PricingSettingsResponse.of(configuration.publishSettings(request.fuelSurchargePct(),
                request.taxPct(), request.minimumCharge(), request.sameDayCutoff(), request.effectiveFrom()));
    }

    @GetMapping("/distance-bands")
    @Operation(summary = "Every published line-haul band, newest set first (ADMIN)")
    public List<DistanceBandResponse> distanceBands() {
        return configuration.bandHistory().stream().map(DistanceBandResponse::of).toList();
    }

    @PostMapping("/distance-bands")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a complete set of line-haul bands from a date (ADMIN)")
    public List<DistanceBandResponse> publishDistanceBands(@Valid @RequestBody DistanceBandSetRequest request) {
        return configuration.publishBands(request.drafts(), request.effectiveFrom()).stream()
                .map(DistanceBandResponse::of).toList();
    }

    @GetMapping("/special-handling")
    @Operation(summary = "Every handling option, including retired ones (ADMIN)")
    public List<SpecialHandlingConfigResponse> specialHandling() {
        return configuration.handlingOptions().stream().map(SpecialHandlingConfigResponse::of).toList();
    }

    @PostMapping("/special-handling")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a handling option (ADMIN)")
    public SpecialHandlingConfigResponse addSpecialHandling(@Valid @RequestBody SpecialHandlingCreateRequest request) {
        return SpecialHandlingConfigResponse.of(configuration.addHandling(request.code(), request.label(),
                request.fee(), request.sortOrder()));
    }

    @PutMapping("/special-handling/{code}")
    @Operation(summary = "Change a handling option, or retire it (ADMIN)")
    public SpecialHandlingConfigResponse updateSpecialHandling(@PathVariable String code,
                                                               @Valid @RequestBody SpecialHandlingUpdateRequest request) {
        return SpecialHandlingConfigResponse.of(configuration.updateHandling(code, request.label(), request.fee(),
                request.active(), request.sortOrder()));
    }

    @GetMapping("/vehicles")
    @Operation(summary = "The same-day vehicles and their limits (ADMIN)")
    public List<SameDayVehicleResponse> vehicles() {
        return configuration.sameDayVehicles().stream().map(SameDayVehicleResponse::of).toList();
    }

    @PutMapping("/vehicles/{code}")
    @Operation(summary = "Change a same-day vehicle's limits, or stop offering it (ADMIN)")
    public SameDayVehicleResponse updateVehicle(@PathVariable VehicleType code,
                                                @Valid @RequestBody SameDayVehicleUpdateRequest request) {
        return SameDayVehicleResponse.of(configuration.updateVehicle(code, request.label(), request.maxWeightKg(),
                request.maxDistanceKm(), request.rateMultiplier(), request.active()));
    }
}
