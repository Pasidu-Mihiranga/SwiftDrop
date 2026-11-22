package lk.swiftdrop.parcel.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.api.dto.ParcelPageResponse;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.api.dto.ProofLinkResponse;
import lk.swiftdrop.parcel.api.dto.ProofUploadRequest;
import lk.swiftdrop.parcel.api.dto.ProofUploadResponse;
import lk.swiftdrop.parcel.api.dto.StatusUpdateRequest;
import lk.swiftdrop.parcel.application.command.DeliveryProofUseCase;
import lk.swiftdrop.parcel.application.command.UpdateParcelStatusUseCase;
import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import lk.swiftdrop.parcel.application.query.ParcelQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Courier operations (M4): the courier's assigned parcels, status updates through the lifecycle,
 * and proof-of-delivery uploads. A courier who asks about a parcel assigned to someone else gets
 * 404, the same as for a parcel that does not exist. Staff may act on any parcel.
 */
@RestController
@Validated
@RequestMapping("/api/v1/courier/parcels")
@Tag(name = "Courier", description = "Courier operations: assigned parcels, status updates, proof of delivery")
public class CourierParcelController {

    private static final String COURIER_OR_STAFF = "hasAnyRole('COURIER', 'DISPATCHER', 'ADMIN')";

    private final UpdateParcelStatusUseCase statuses;
    private final DeliveryProofUseCase proofs;
    private final ParcelQueryService parcels;

    public CourierParcelController(UpdateParcelStatusUseCase statuses, DeliveryProofUseCase proofs,
                                   ParcelQueryService parcels) {
        this.statuses = statuses;
        this.proofs = proofs;
        this.parcels = parcels;
    }

    @GetMapping
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "The parcels assigned to the calling courier, newest first (COURIER)")
    public ParcelPageResponse assigned(@RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
                                       @RequestParam(required = false) ParcelStatus status,
                                       AuthenticatedUser courier) {
        return ParcelPageResponse.from(parcels.assignedTo(courier, status, page, size));
    }

    @PatchMapping("/{parcelId}/status")
    @PreAuthorize(COURIER_OR_STAFF)
    @Operation(summary = "Move an assigned parcel to its next status; FAILED needs a reason, DELIVERED needs proof")
    public ParcelResponse updateStatus(@PathVariable UUID parcelId, @Valid @RequestBody StatusUpdateRequest request,
                                       AuthenticatedUser caller) {
        return ParcelResponse.detailed(statuses.update(caller, parcelId, request.toCommand()));
    }

    @PostMapping("/{parcelId}/proof-upload-url")
    @PreAuthorize(COURIER_OR_STAFF)
    @Operation(summary = "A presigned URL to upload a proof-of-delivery photo (JPEG or PNG, at most 5 MB)")
    public ProofUploadResponse proofUploadUrl(@PathVariable UUID parcelId, @Valid @RequestBody ProofUploadRequest request,
                                              AuthenticatedUser caller) {
        DeliveryProofUseCase.ProofUpload upload = proofs.requestUpload(caller, parcelId, request.contentType(),
                request.sizeBytes());
        return new ProofUploadResponse(upload.proofId(), upload.uploadUrl(), upload.expiresAt());
    }

    @GetMapping("/{parcelId}/proofs/{proofId}")
    @Operation(summary = "A short-lived link to view a proof-of-delivery file (anyone who may see the parcel)")
    public ProofLinkResponse proofLink(@PathVariable UUID parcelId, @PathVariable UUID proofId,
                                       AuthenticatedUser caller) {
        PresignedUrl link = proofs.viewLink(caller, parcelId, proofId);
        return new ProofLinkResponse(link.url(), link.expiresAt());
    }
}
