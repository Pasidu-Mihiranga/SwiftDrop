package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.pricing.api.dto.QuoteRequest;
import lk.swiftdrop.pricing.api.dto.QuoteResponse;
import lk.swiftdrop.pricing.api.mapper.QuoteApiMapper;
import lk.swiftdrop.pricing.application.command.QuoteIssuanceService;
import lk.swiftdrop.pricing.application.query.QuoteLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pricing/quotes")
@Tag(name = "quotes", description = "Public fee estimates and quote validation")
public class QuoteController {

    private final QuoteIssuanceService quotes;
    private final QuoteLookupService lookup;

    public QuoteController(QuoteIssuanceService quotes, QuoteLookupService lookup) {
        this.quotes = quotes;
        this.lookup = lookup;
    }

    /**
     * Issues a quote, applying a merchant's negotiated terms when the caller presents a token
     * that carries them.
     *
     * <p>The path stays anonymous: a visitor must be able to price a parcel before signing up.
     * The resolver yields null when there is no token, so the parameter is simply absent for
     * most callers and the quote is priced at the counter rate. No security configuration
     * changes to make this work, because a valid token on a permitted path still populates the
     * security context.
     */
    @PostMapping
    @Operation(summary = "Issue a fee quote (public; a merchant token applies negotiated rates)")
    public QuoteResponse create(@Valid @RequestBody QuoteRequest request, AuthenticatedUser caller) {
        return QuoteApiMapper.toResponse(quotes.issue(request.toPricingInput(merchantIdOf(caller))));
    }

    /**
     * A malformed merchant claim means no agreement, never a failed quote. A broken token is
     * not a reason to refuse a public estimate.
     */
    private static UUID merchantIdOf(AuthenticatedUser caller) {
        if (caller == null) {
            return null;
        }
        return caller.merchantId().map(QuoteController::parseOrNull).orElse(null);
    }

    private static UUID parseOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    @GetMapping("/{quoteId}")
    @Operation(summary = "Validate an issued quote", security = @SecurityRequirement(name = "bearerAuth"))
    public QuoteResponse get(@PathVariable UUID quoteId) {
        return QuoteApiMapper.toResponse(lookup.get(quoteId));
    }
}
