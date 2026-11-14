package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.parcel.application.command.CreateParcelCommand;

import java.util.UUID;

/**
 * A customer confirms a quote. The weight, size class, service level and fee are not accepted
 * here: they are taken from the quote, so a caller cannot book at a price it chose itself.
 *
 * @param quoteId from POST /api/v1/pricing/quotes, valid for 15 minutes
 */
public record CreateParcelRequest(
        @NotNull UUID quoteId,
        @NotNull @Valid AddressDto pickup,
        @NotNull @Valid AddressDto dropoff,
        @NotNull @Valid ContactDto recipient,
        @Size(max = 300) String notes) {

    public CreateParcelCommand toCommand() {
        return new CreateParcelCommand(quoteId, pickup.toDomain(), dropoff.toDomain(), recipient.toDomain(),
                notes == null || notes.isBlank() ? null : notes.strip());
    }
}
