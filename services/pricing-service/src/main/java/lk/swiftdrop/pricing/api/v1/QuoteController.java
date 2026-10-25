package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.QuoteRequest;
import lk.swiftdrop.pricing.application.command.QuoteIssuanceService;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
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

    @PostMapping
    @Operation(summary = "Issue a fee quote (public)")
    public QuoteSnapshot create(@Valid @RequestBody QuoteRequest request) {
        return quotes.issue(request.toPricingInput());
    }

    @GetMapping("/{quoteId}")
    @Operation(summary = "Validate an issued quote", security = @SecurityRequirement(name = "bearerAuth"))
    public QuoteSnapshot get(@PathVariable UUID quoteId) {
        return lookup.get(quoteId);
    }
}
