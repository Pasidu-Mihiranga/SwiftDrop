package lk.swiftdrop.pricing.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.MerchantAgreementRequest;
import lk.swiftdrop.pricing.api.dto.MerchantAgreementResponse;
import lk.swiftdrop.pricing.application.command.MerchantAgreementAdminService;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The terms a merchant ships under.
 *
 * <p>Publishing closes whatever was in force and inserts a replacement, so an agreement that
 * has expired can still explain a quote issued while it applied.
 */
@RestController
@RequestMapping("/api/v1/admin/pricing/merchants")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "admin-pricing", description = "Pricing configuration (ADMIN)")
public class AdminMerchantAgreementController {

    private final MerchantAgreementAdminService agreements;

    public AdminMerchantAgreementController(MerchantAgreementAdminService agreements) {
        this.agreements = agreements;
    }

    @GetMapping
    @Operation(summary = "List the agreements in force today, one per merchant (ADMIN)")
    public List<MerchantAgreementResponse> listInForce() {
        return agreements.listInForce().stream().map(MerchantAgreementResponse::of).toList();
    }

    @GetMapping("/{merchantId}/agreements")
    @Operation(summary = "List a merchant's agreements, newest first (ADMIN)")
    public List<MerchantAgreementResponse> list(@PathVariable UUID merchantId) {
        return agreements.history(merchantId).stream()
                .map(MerchantAgreementResponse::of)
                .toList();
    }

    @PutMapping("/{merchantId}/agreements")
    @Operation(summary = "Publish a merchant's terms from a date (ADMIN)")
    public MerchantAgreementResponse publish(@PathVariable UUID merchantId,
                                             @Valid @RequestBody MerchantAgreementRequest request) {
        List<LaneOverride> overrides = request.laneOverridesOrEmpty().stream()
                .map(lane -> new LaneOverride(ZoneCode.of(lane.fromZone()), ZoneCode.of(lane.toZone()),
                        lane.discountPct(), lane.flatBaseFee()))
                .toList();
        MerchantAgreement candidate = new MerchantAgreement(null, merchantId, request.discountPct(),
                request.discountFlat(), request.waivesRemoteAreaOrDefault(),
                request.waivesInsuranceOrDefault(), request.waivesCodFeeOrDefault(), overrides);
        return MerchantAgreementResponse.of(agreements.publish(candidate, request.effectiveFrom()));
    }
}
