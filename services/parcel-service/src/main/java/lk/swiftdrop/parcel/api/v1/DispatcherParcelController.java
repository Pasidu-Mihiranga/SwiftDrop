package lk.swiftdrop.parcel.api.v1;

import jakarta.validation.constraints.Size;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.application.ParcelStatusService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/dispatcher/parcels")
public class DispatcherParcelController {
    private final ParcelStatusService statuses;
    public DispatcherParcelController(ParcelStatusService statuses) { this.statuses=statuses; }
    @PostMapping("/{id}/reattempt")
    public Object reattempt(@PathVariable UUID id, @RequestParam(required=false) @Size(max=500) String note, AuthenticatedUser user) {
        return statuses.reattempt(id,note,user);
    }
}
