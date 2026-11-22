package lk.swiftdrop.parcel.api.v1;

import jakarta.validation.constraints.Size;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.api.dto.ParcelResponse;
import lk.swiftdrop.parcel.application.command.CourierStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/dispatcher/parcels")
@PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
public class DispatcherParcelController {

    private final CourierStatusService statuses;

    public DispatcherParcelController(CourierStatusService statuses) {
        this.statuses = statuses;
    }

    @PostMapping("/{parcelId}/reattempt")
    public ParcelResponse reattempt(@PathVariable UUID parcelId,
                                    @RequestParam(required = false) @Size(max = 500) String note,
                                    AuthenticatedUser caller) {
        return ParcelResponse.detailed(statuses.reattempt(parcelId, note, caller));
    }
}
