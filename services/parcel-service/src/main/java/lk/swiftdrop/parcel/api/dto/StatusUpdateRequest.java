package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.command.StatusUpdate;
import lk.swiftdrop.parcel.domain.model.GeoPoint;
import lk.swiftdrop.parcel.domain.model.ProofOfDelivery;

import java.math.BigDecimal;

/** The StatusUpdateRequest schema in contracts/openapi/parcel-service.v1.yaml. */
public record StatusUpdateRequest(
        @NotNull ParcelStatus toStatus,
        @Size(max = 500) String note,
        FailureReason failureReason,
        @Valid Proof proof,
        @Valid Position location) {

    public record Proof(@NotNull ProofOfDelivery.Type type, @Size(max = 100) String receivedBy,
                        @Size(max = 64) String proofId) {
    }

    public record Position(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
                           @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {
    }

    public StatusUpdate toCommand() {
        ProofOfDelivery delivered = proof == null ? null
                : new ProofOfDelivery(proof.type(), blankToNull(proof.receivedBy()), blankToNull(proof.proofId()));
        GeoPoint position = location == null ? null : new GeoPoint(location.lat(), location.lng());
        return new StatusUpdate(toStatus, blankToNull(note), failureReason, delivered, position);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
