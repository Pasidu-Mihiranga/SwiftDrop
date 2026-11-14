package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.Recipient;

import java.util.UUID;

/** A customer's confirmation of a quote. Weight, size and fee are not here: they come from the quote. */
public record CreateParcelCommand(UUID quoteId, Address pickup, Address dropoff, Recipient recipient, String notes) {
}
