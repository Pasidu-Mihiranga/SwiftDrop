package lk.swiftdrop.user.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.user.api.dto.CourierProfileRequest;
import lk.swiftdrop.user.api.dto.CourierResponse;
import lk.swiftdrop.user.api.mapper.CourierApiMapper;
import lk.swiftdrop.user.application.command.CourierProfileService;
import lk.swiftdrop.user.application.query.CourierDirectoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The courier directory for dispatch (M5): who the couriers are, what they drive, where they work
 * and how much they can carry. Dispatchers read it; only admins change it.
 */
@RestController
@RequestMapping("/api/v1/admin/couriers")
@Tag(name = "Couriers", description = "Courier directory for dispatch (DISPATCHER, ADMIN)")
public class AdminCourierController {

    private final CourierDirectoryService directory;
    private final CourierProfileService profiles;

    public AdminCourierController(CourierDirectoryService directory, CourierProfileService profiles) {
        this.directory = directory;
        this.profiles = profiles;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    @Operation(summary = "Couriers with vehicle, home zone and daily capacity, sorted by name (DISPATCHER, ADMIN)")
    public List<CourierResponse> couriers(@RequestParam(required = false) UUID zoneId,
                                          @RequestParam(required = false) Boolean assignable) {
        return directory.couriers(zoneId, assignable).stream().map(CourierApiMapper::toResponse).toList();
    }

    @PutMapping("/{courierId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set a courier's vehicle, home zone, daily capacity and availability (ADMIN)")
    public CourierResponse updateProfile(@PathVariable String courierId, @Valid @RequestBody CourierProfileRequest request) {
        return CourierApiMapper.toResponse(profiles.updateProfile(courierId, request.vehicleType(), request.homeZoneId(),
                request.maxDailyCapacity(), request.activeOrDefault()));
    }
}
