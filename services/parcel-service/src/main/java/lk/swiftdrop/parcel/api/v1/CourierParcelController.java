package lk.swiftdrop.parcel.api.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.api.dto.*;
import lk.swiftdrop.parcel.application.ParcelStatusService;
import lk.swiftdrop.parcel.application.ProofService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController @Validated @RequestMapping("/api/v1/courier/parcels")
public class CourierParcelController {
    private final ParcelStatusService statuses; private final ProofService proofs;
    public CourierParcelController(ParcelStatusService statuses, ProofService proofs) { this.statuses=statuses; this.proofs=proofs; }
    @GetMapping
    public List<CourierParcelResponse> assignedParcels(AuthenticatedUser user) {
        return statuses.assignedTo(user).stream().map(p -> new CourierParcelResponse(p.getId(), p.getTrackingNumber(), p.getStatus(), p.getCustomerId(), p.getMerchantId(), p.getCourierId())).toList();
    }
    @PatchMapping("/{id}/status")
    public Object update(@PathVariable UUID id, @Valid @RequestBody UpdateParcelStatusRequest request, AuthenticatedUser user) {
        return statuses.update(id,request.status(),request.note(),request.failureReason(),request.proof(),request.location(),user);
    }
    @PostMapping("/{id}/proof-upload-url") @ResponseStatus(HttpStatus.CREATED)
    public ProofUploadResponse uploadUrl(@PathVariable UUID id, @RequestParam @NotBlank @Pattern(regexp="image/(jpeg|png)") String contentType,
                                         @RequestParam @Positive @Max(ProofService.MAX_BYTES) long sizeBytes, AuthenticatedUser user) {
        var proof=proofs.create(id,contentType,sizeBytes,user); return new ProofUploadResponse(proof.proofId(),proof.uploadUrl(),proof.contentType(),proof.maxBytes());
    }
    @GetMapping("/{id}/proof/{proofId}")
    public String proofUrl(@PathVariable UUID id, @PathVariable UUID proofId, AuthenticatedUser user) {
        return proofs.download(id,proofId,user);
    }
}
