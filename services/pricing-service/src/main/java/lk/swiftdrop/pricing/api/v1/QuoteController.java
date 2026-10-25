package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.pricing.api.dto.QuoteRequest;
import lk.swiftdrop.pricing.application.command.QuoteIssuanceService;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing/quotes")
@Tag(name = "quotes", description = "Public fee estimates and quote validation")
public class QuoteController {

    private final QuoteIssuanceService quotes;

    public QuoteController(QuoteIssuanceService quotes) {
        this.quotes = quotes;
    }

    @PostMapping
    @Operation(summary = "Issue a fee quote (public)")
    public QuoteSnapshot create(@Valid @RequestBody QuoteRequest request) {
        return quotes.issue(request.toPricingInput());
    }
}
