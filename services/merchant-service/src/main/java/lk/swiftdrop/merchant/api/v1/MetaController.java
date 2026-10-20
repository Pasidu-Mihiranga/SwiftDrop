package lk.swiftdrop.merchant.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.swiftdrop.merchant.api.dto.WhoAmIResponse;
import lk.swiftdrop.common.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smoke-test endpoint: proves the gateway → service → JWT chain works end to end. */
@RestController
@RequestMapping("/api/v1/merchants/meta")
@Tag(name = "Meta", description = "Diagnostics")
public class MetaController {

    @GetMapping("/whoami")
    @Operation(summary = "Returns the caller as seen by merchant-service")
    public WhoAmIResponse whoAmI(AuthenticatedUser user) {
        return new WhoAmIResponse("merchant-service", user.id(), user.roles(), user.merchantId().orElse(null));
    }
}
