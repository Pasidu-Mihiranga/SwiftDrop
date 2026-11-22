package lk.swiftdrop.merchant.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.api.dto.MerchantPageResponse;
import lk.swiftdrop.merchant.api.dto.MerchantResponse;
import lk.swiftdrop.merchant.api.dto.RejectMerchantRequest;
import lk.swiftdrop.merchant.application.command.MerchantOnboardingService;
import lk.swiftdrop.merchant.application.query.MerchantQueryService;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The admin approval queue. Every endpoint is ADMIN only; the state rules (which decision is
 * allowed from which status) are enforced by the merchant itself and answered with 422.
 */
@RestController
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: merchants", description = "Review, approve, reject and suspend merchants (ADMIN)")
public class AdminMerchantController {

    private final MerchantOnboardingService onboarding;
    private final MerchantQueryService merchants;

    public AdminMerchantController(MerchantOnboardingService onboarding, MerchantQueryService merchants) {
        this.onboarding = onboarding;
        this.merchants = merchants;
    }

    @GetMapping
    @Operation(summary = "A page of applications in one status, oldest first (default PENDING: the approval queue)")
    public MerchantPageResponse list(@RequestParam(defaultValue = "PENDING") MerchantStatus status,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return MerchantPageResponse.from(merchants.byStatus(status, page, size));
    }

    @PostMapping("/{merchantId}/approve")
    @Operation(summary = "Approve a PENDING application (publishes MerchantLifecycleChanged)")
    public MerchantResponse approve(@PathVariable UUID merchantId, AuthenticatedUser admin) {
        return MerchantResponse.from(onboarding.approve(admin, merchantId));
    }

    @PostMapping("/{merchantId}/reject")
    @Operation(summary = "Reject a PENDING application with a reason the applicant can read")
    public MerchantResponse reject(@PathVariable UUID merchantId, @Valid @RequestBody RejectMerchantRequest request,
                                   AuthenticatedUser admin) {
        return MerchantResponse.from(onboarding.reject(admin, merchantId, request.reason()));
    }

    @PostMapping("/{merchantId}/suspend")
    @Operation(summary = "Suspend an APPROVED merchant: its API orders are refused from then on")
    public MerchantResponse suspend(@PathVariable UUID merchantId, AuthenticatedUser admin) {
        return MerchantResponse.from(onboarding.suspend(admin, merchantId));
    }
}
