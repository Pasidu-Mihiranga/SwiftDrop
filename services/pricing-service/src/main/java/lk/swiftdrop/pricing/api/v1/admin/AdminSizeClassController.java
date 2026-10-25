package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.SizeClassUpdateRequest;
import lk.swiftdrop.pricing.application.command.SizeClassAdminService;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing/size-classes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-size-classes", description = "Size-class administration (ADMIN)")
public class AdminSizeClassController {

    private final SizeClassAdminService sizeClasses;

    public AdminSizeClassController(SizeClassAdminService sizeClasses) {
        this.sizeClasses = sizeClasses;
    }

    @GetMapping
    @Operation(summary = "List configured size classes (ADMIN)")
    public List<SizeClassRate> list() {
        return sizeClasses.list();
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update an existing size class (ADMIN)")
    public SizeClassRate update(@PathVariable SizeClassCode code, @Valid @RequestBody SizeClassUpdateRequest request) {
        return sizeClasses.update(code, request.maxWeightKg(), request.maxDimensionsCm(), request.multiplier());
    }
}
