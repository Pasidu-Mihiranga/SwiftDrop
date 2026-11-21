package lk.swiftdrop.dispatch.api.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lk.swiftdrop.dispatch.application.DispatchService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;

@RestController
public class DispatchController {
    private final DispatchService service;
    public DispatchController(DispatchService service) { this.service = service; }
    public record AssignRequest(@NotNull UUID parcelId, @NotBlank @Size(max=255) String courierId, LocalDate runDate) { }
    public record ReassignRequest(@NotBlank @Size(max=255) String courierId) { }
    @GetMapping("/api/v1/dispatch/unassigned")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public Page unassigned(@RequestParam(required=false) String zone, @RequestParam(defaultValue="0") @Min(0) int page,
                           @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.unassigned(zone, page, size);
    }
    @GetMapping("/api/v1/dispatch/workloads")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public List<Workload> workloads(@AuthenticationPrincipal Jwt jwt) { return service.workloads(jwt.getTokenValue()); }
    @GetMapping("/api/v1/dispatch/assignments")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public List<Parcel> assignments(@RequestParam(required=false) LocalDate date) { return service.runs(date, null); }
    @PostMapping("/api/v1/dispatch/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public Allocation assign(@Valid @RequestBody AssignRequest request, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.assign(request.parcelId(), request.courierId(), request.runDate(), jwt.getSubject(), admin(auth), jwt.getTokenValue());
    }
    @PostMapping("/api/v1/dispatch/assignments/{id}/reassign")
    @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public Allocation reassign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest request,
                               @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return service.reassign(id, request.courierId(), jwt.getSubject(), admin(auth), jwt.getTokenValue());
    }
    @GetMapping("/api/v1/courier/run-sheet")
    @PreAuthorize("hasRole('COURIER')")
    public List<Parcel> runSheet(@RequestParam(required=false) LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        return service.runs(date, jwt.getSubject());
    }
    private boolean admin(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }
}
