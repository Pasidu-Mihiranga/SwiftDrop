package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.constraints.Size;

/** Optional note for the courier who takes the parcel out again. */
public record ReattemptRequest(@Size(max = 500) String note) {
}
