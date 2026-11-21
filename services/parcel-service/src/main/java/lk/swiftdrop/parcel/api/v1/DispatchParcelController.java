package lk.swiftdrop.parcel.api.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.api.dto.ReattemptRequest;
import lk.swiftdrop.parcel.application.command.UpdateParcelStatusUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Dispatch's decisions on a parcel's lifecycle (M4): sending a failed delivery out again. */
@RestController
@RequestMapping("/api/v1/parcels")
@Tag(name = "Dispatch", description = "Dispatch actions on a parcel's lifecycle")
public class DispatchParcelController {

    private final UpdateParcelStatusUseCase statuses;

    public DispatchParcelController(UpdateParcelStatusUseCase statuses) {
        this.statuses = statuses;
    }

    @PostMapping("/{parcelId}/reattempt")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    @Operation(summary = "Send a failed delivery out again: FAILED to OUT_FOR_DELIVERY (DISPATCHER, ADMIN)")
    public ParcelResponse reattempt(@PathVariable UUID parcelId,
                                    @Valid @RequestBody(required = false) ReattemptRequest request,
                                    AuthenticatedUser caller) {
        return ParcelResponse.detailed(statuses.reattempt(caller, parcelId, request == null ? null : request.note()));
    }
}
