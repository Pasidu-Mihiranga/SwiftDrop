package lk.swiftdrop.merchant.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.api.dto.MerchantResponse;
import lk.swiftdrop.merchant.api.dto.RegisterMerchantRequest;
import lk.swiftdrop.merchant.application.command.MerchantOnboardingService;
import lk.swiftdrop.merchant.application.query.MerchantQueryService;
import lk.swiftdrop.merchant.domain.model.Merchant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * The applicant's side of onboarding. The caller is always the owner: no owner id is read from
 * the request, so one user can neither apply for nor read another user's application.
 */
@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchant", description = "Apply to become a merchant and follow the application")
public class MerchantController {

    /** A person with a SwiftDrop account, not a merchant's machine client. */
    private static final String SIGNED_IN_PERSON = "isAuthenticated() and !hasRole('MERCHANT_API')";
    private final MerchantOnboardingService onboarding;
    private final MerchantQueryService merchants;

    public MerchantController(MerchantOnboardingService onboarding, MerchantQueryService merchants) {
        this.onboarding = onboarding;
        this.merchants = merchants;
    }

    @PostMapping("/register")
    @PreAuthorize(SIGNED_IN_PERSON)
    @Operation(summary = "Apply to become a merchant (any signed-in user); starts PENDING")
    public ResponseEntity<MerchantResponse> register(@Valid @RequestBody RegisterMerchantRequest request,
                                                     AuthenticatedUser applicant) {
        Merchant merchant = onboarding.register(applicant, request.toDomain());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/merchants/me").build().toUri();
        return ResponseEntity.created(location).body(MerchantResponse.from(merchant));
    }

    @GetMapping("/me")
    @PreAuthorize(SIGNED_IN_PERSON)
    @Operation(summary = "The caller's latest application and its status, including a rejection reason")
    public MerchantResponse me(AuthenticatedUser caller) {
        return MerchantResponse.from(merchants.mine(caller.id()));
    }
}
