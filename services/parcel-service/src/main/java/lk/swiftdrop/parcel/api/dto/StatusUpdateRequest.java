package lk.swiftdrop.parcel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.events.common.Types;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;

/** Request body for a courier lifecycle transition. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusUpdateRequest(
        @NotNull Types.ParcelStatus toStatus,
        @Size(max = 500) String note,
        Types.FailureReason failureReason,
        @Valid Proof proof,
        Types.Location location) {

    public ParcelStatusChanged.Proof eventProof() {
        return proof == null ? null : new ParcelStatusChanged.Proof(proof.type(), proof.receivedBy(), proof.proofId());
    }

    public record Proof(@NotNull ParcelStatusChanged.ProofType type, @Size(max = 100) String receivedBy,
                        @Size(max = 100) String proofId) {
    }
}
