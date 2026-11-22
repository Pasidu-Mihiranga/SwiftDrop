package lk.swiftdrop.reporting.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.reporting.application.command.RebuildService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Rebuilds the reports from the event log, for example after a projection bug fix (ADMIN only). */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reports", description = "Delivery statistics (DISPATCHER, ADMIN)")
public class ReportRebuildController {

    private final RebuildService rebuild;

    public ReportRebuildController(RebuildService rebuild) {
        this.rebuild = rebuild;
    }

    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Empty the projections and replay the parcel topics from the beginning (ADMIN)")
    public Map<String, String> rebuild() {
        rebuild.rebuild();
        return Map.of("status", "REBUILDING");
    }
}
