package lk.swiftdrop.pricing.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.pricing.api.dto.AddressSuggestionResponse;
import lk.swiftdrop.pricing.application.query.AddressSearchService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Address search for the quote and booking maps (public, like {@code /locate}). Search runs when
 * the customer asks for it, never as they type: public geocoders forbid autocomplete use.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@Tag(name = "zones", description = "Public service-zone coverage")
public class AddressSearchController {

    private final AddressSearchService addresses;

    public AddressSearchController(AddressSearchService addresses) {
        this.addresses = addresses;
    }

    @GetMapping("/address-search")
    @Operation(summary = "Places matching an address, inside the service area, with their district (public)")
    public ResponseEntity<List<AddressSuggestionResponse>> search(@RequestParam("q") String query) {
        List<AddressSuggestionResponse> suggestions = addresses.search(query).stream()
                .map(AddressSuggestionResponse::of).toList();
        // An address is personal data: no shared cache should keep the answer.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(suggestions);
    }
}
