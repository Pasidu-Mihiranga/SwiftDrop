package lk.swiftdrop.parcel.api.dto;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lk.swiftdrop.events.common.Types;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
public record UpdateParcelStatusRequest(@NotNull Types.ParcelStatus status, @Size(max=500) String note,
                                        Types.FailureReason failureReason, ParcelStatusChanged.Proof proof,
                                        Types.Location location) { }
