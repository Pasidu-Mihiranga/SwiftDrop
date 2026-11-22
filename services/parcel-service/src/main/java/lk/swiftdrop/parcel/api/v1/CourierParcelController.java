package lk.swiftdrop.parcel.api.v1;

import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.api.dto.ProofUploadResponse;
import lk.swiftdrop.parcel.api.dto.ProofUploadRequest;
import lk.swiftdrop.parcel.api.dto.StatusUpdateRequest;
import lk.swiftdrop.parcel.application.command.CourierStatusService;
import lk.swiftdrop.parcel.application.command.ProofUploadService;
import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/courier/parcels")
@PreAuthorize("hasAnyRole('COURIER','ADMIN')")
public class CourierParcelController {

    private final CourierStatusService statuses;
    private final ProofUploadService proofs;
    public CourierParcelController(CourierStatusService statuses, ProofUploadService proofs) {
        this.statuses = statuses;
        this.proofs = proofs;
    }

    @PatchMapping("/{parcelId}/status")
    public ParcelResponse updateStatus(@PathVariable UUID parcelId, @Valid @RequestBody StatusUpdateRequest request,
                                       AuthenticatedUser caller) {
        return ParcelResponse.detailed(statuses.update(parcelId, request.toStatus(), request.note(),
                request.failureReason(), request.eventProof(), request.location(), caller));
    }

    @PostMapping("/{parcelId}/proof-upload-url")
    public ProofUploadResponse proofUploadUrl(@PathVariable UUID parcelId, @Valid @RequestBody ProofUploadRequest request,
                                              AuthenticatedUser caller) {
        ProofUploadService.Upload upload = proofs.create(parcelId, request.contentType(), request.sizeBytes(), caller);
        return new ProofUploadResponse(upload.proofId(), upload.uploadUrl(), upload.expiresAt());
    }

    @GetMapping("/{parcelId}/proof/{proofId}")
    public ResponseEntity<String> proofDownloadUrl(@PathVariable UUID parcelId, @PathVariable UUID proofId,
                                                   AuthenticatedUser caller) {
        ProofStorage.Download download = proofs.download(parcelId, proofId, caller);
        return ResponseEntity.ok(download.url());
    }
}
