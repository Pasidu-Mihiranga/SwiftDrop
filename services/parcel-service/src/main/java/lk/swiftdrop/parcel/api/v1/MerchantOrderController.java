package lk.swiftdrop.parcel.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.api.dto.MerchantOrderRequest;
import lk.swiftdrop.parcel.api.dto.ParcelPageResponse;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.application.command.MerchantOrderService;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.MerchantOrderResult;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;

/**
 * The merchant order API (design figures 7, 9 and 10). The merchant is always the one in the
 * caller's token: no merchant id is accepted from the path, query or body, so no merchant can
 * create or read another merchant's orders.
 */
@RestController
@RequestMapping("/api/v1/merchant/orders")
@Tag(name = "Merchant API", description = "Merchant systems create delivery orders; merchants list their orders")
public class MerchantOrderController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Set on a 200 that repeats an earlier answer, so a merchant can tell a replay from a new order. */
    public static final String REPLAYED = "Idempotent-Replayed";

    /** A merchant's own system, signed in with client credentials. */
    private static final String MERCHANT_SYSTEM = "hasRole('MERCHANT_API')";
    /** The merchant's system, or a person in the merchant portal. */
    private static final String MERCHANT_OR_ITS_SYSTEM = "hasAnyRole('MERCHANT', 'MERCHANT_API')";

    private final MerchantOrderService orders;

    public MerchantOrderController(MerchantOrderService orders) {
        this.orders = orders;
    }

    /**
     * The header is read as optional so that a missing key gets the same 400 IDEMPOTENCY_KEY_INVALID
     * as a malformed one.
     */
    @PostMapping
    @PreAuthorize(MERCHANT_SYSTEM)
    @Operation(summary = "Create a delivery order (MERCHANT_API); repeating it with the same Idempotency-Key "
            + "returns the original order")
    public ResponseEntity<ParcelResponse> create(@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                                                 @Valid @RequestBody MerchantOrderRequest request,
                                                 AuthenticatedUser caller) {
        MerchantOrderResult result = orders.create(caller, new IdempotencyKey(key), request.toCommand());
        ParcelResponse body = ParcelResponse.detailed(result.parcel());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/parcels/{id}").build(result.parcel().id());
        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).location(location).header(REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @PreAuthorize(MERCHANT_OR_ITS_SYSTEM)
    @Operation(summary = "The caller's merchant's orders, newest first, filtered by status and creation time")
    public ParcelPageResponse list(@RequestParam(required = false) ParcelStatus status,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                   AuthenticatedUser caller) {
        return ParcelPageResponse.from(orders.list(caller, new MerchantOrderFilter(status, from, to), page, size));
    }
}
