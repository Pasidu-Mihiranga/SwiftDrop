package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.pricing.api.dto.RateCardClosureRequest;
import lk.swiftdrop.pricing.api.dto.RateCardCreateRequest;
import lk.swiftdrop.pricing.api.dto.RateCardPageResponse;
import lk.swiftdrop.pricing.api.dto.RateCardResponse;
import lk.swiftdrop.pricing.api.mapper.RateCardApiMapper;
import lk.swiftdrop.pricing.application.command.RateCardAdminService;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.query.RateCardQueryService;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/** Rate-card maintenance. Pricing configuration is administrator-only. */
@RestController
@RequestMapping("/api/v1/admin/pricing/rate-cards")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "admin-rate-cards", description = "Rate-card administration (ADMIN)")
public class AdminRateCardController {

    private final RateCardQueryService queries;
    private final RateCardAdminService commands;

    public AdminRateCardController(RateCardQueryService queries, RateCardAdminService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    @Operation(summary = "List rate cards (ADMIN)")
    public RateCardPageResponse list(
            @RequestParam(required = false) String fromZone,
            @RequestParam(required = false) String toZone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        RateCardFilter filter = new RateCardFilter(zoneCode(fromZone), zoneCode(toZone), activeOn);
        return RateCardApiMapper.toResponse(queries.list(filter, page, size));
    }

    @GetMapping("/{rateCardId}")
    @Operation(summary = "Get one rate card (ADMIN)")
    public RateCardResponse get(@PathVariable UUID rateCardId) {
        return RateCardApiMapper.toResponse(queries.find(rateCardId));
    }

    @PostMapping
    @Operation(summary = "Create rate card (ADMIN). Overlapping periods for a lane are rejected.")
    public ResponseEntity<RateCardResponse> create(@Valid @RequestBody RateCardCreateRequest request) {
        RateCard created = commands.create(ZoneCode.of(request.fromZone()), ZoneCode.of(request.toZone()),
                request.baseFee(), request.perKgFee(), request.expressMultiplier(), request.codFeePct(),
                request.effectiveFrom(), request.effectiveTo());
        return ResponseEntity
                .created(URI.create("/api/v1/admin/pricing/rate-cards/" + created.id()))
                .body(RateCardApiMapper.toResponse(created));
    }

    @PutMapping("/{rateCardId}/closure")
    @Operation(summary = "Close a rate card by setting its end date (ADMIN). Cards are never deleted.")
    public RateCardResponse close(@PathVariable UUID rateCardId,
                                  @Valid @RequestBody RateCardClosureRequest request) {
        return RateCardApiMapper.toResponse(commands.close(rateCardId, request.effectiveTo()));
    }

    /**
     * A malformed code is a bad filter value, not a missing zone, and the domain reports it with
     * an IllegalArgumentException that would otherwise surface as a 500.
     */
    private static ZoneCode zoneCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZoneCode.of(value);
        } catch (IllegalArgumentException malformed) {
            throw new BadRequestException("INVALID_ZONE_CODE", malformed.getMessage());
        }
    }
}
