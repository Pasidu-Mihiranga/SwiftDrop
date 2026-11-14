package lk.swiftdrop.parcel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.model.StatusChange;

import java.time.Instant;

/** One line of a parcel's history. The actor's id is not published, only the role. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusHistoryEntryResponse(ParcelStatus fromStatus, ParcelStatus toStatus, String note,
                                         ActorRole actorRole, Instant occurredAt) {

    public static StatusHistoryEntryResponse from(StatusChange change) {
        return new StatusHistoryEntryResponse(change.fromStatus(), change.toStatus(), change.note(),
                change.actorRole(), change.occurredAt());
    }
}
