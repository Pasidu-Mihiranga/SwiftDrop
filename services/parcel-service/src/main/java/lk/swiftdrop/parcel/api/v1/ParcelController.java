package lk.swiftdrop.parcel.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.api.dto.CreateParcelRequest;
import lk.swiftdrop.parcel.api.dto.ParcelPageResponse;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.application.command.CancelParcelUseCase;
import lk.swiftdrop.parcel.application.command.CreateParcelUseCase;
import lk.swiftdrop.parcel.application.query.ParcelQueryService;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Customer booking. The controller only validates the request and delegates: the use cases book
 * and cancel, and the access policy decides who may see a parcel.
 */
@RestController
@RequestMapping("/api/v1/parcels")
@Tag(name = "Customer", description = "Customer booking: create, list, open and cancel a delivery")
public class ParcelController {

    private static final String CUSTOMER_ONLY = "hasRole('CUSTOMER')";

    private final CreateParcelUseCase createParcel;
    private final CancelParcelUseCase cancelParcel;
    private final ParcelQueryService parcels;

    public ParcelController(CreateParcelUseCase createParcel, CancelParcelUseCase cancelParcel,
                            ParcelQueryService parcels) {
        this.createParcel = createParcel;
        this.cancelParcel = cancelParcel;
        this.parcels = parcels;
    }

    @PostMapping
    @PreAuthorize(CUSTOMER_ONLY)
    @Operation(summary = "Create a delivery from a confirmed quote (CUSTOMER)")
    public ResponseEntity<ParcelResponse> create(@Valid @RequestBody CreateParcelRequest request,
                                                 AuthenticatedUser customer) {
        Parcel parcel = createParcel.create(customer, request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(parcel.id());
        return ResponseEntity.created(location).body(ParcelResponse.detailed(parcel));
    }

    @GetMapping("/mine")
    @PreAuthorize(CUSTOMER_ONLY)
    @Operation(summary = "The caller's parcels, newest first (CUSTOMER)")
    public ParcelPageResponse mine(@RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                   @RequestParam(required = false) ParcelStatus status,
                                   AuthenticatedUser customer) {
        return ParcelPageResponse.from(parcels.mine(customer, status, page, size));
    }

    @GetMapping("/{parcelId}")
    @Operation(summary = "Parcel details for its owner, assigned courier, merchant or staff; 404 for anyone else")
    public ParcelResponse get(@PathVariable UUID parcelId, AuthenticatedUser caller) {
        return ParcelResponse.detailed(parcels.get(caller, parcelId));
    }

    @PostMapping("/{parcelId}/cancel")
    @Operation(summary = "Cancel a parcel that has not been picked up (its owner only)")
    public ParcelResponse cancel(@PathVariable UUID parcelId, AuthenticatedUser caller) {
        return ParcelResponse.detailed(cancelParcel.cancel(caller, parcelId));
    }
}
