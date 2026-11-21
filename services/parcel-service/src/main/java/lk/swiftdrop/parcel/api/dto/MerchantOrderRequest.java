package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.application.command.MerchantOrderCommand;
import lk.swiftdrop.parcel.domain.model.Parcel;

import java.math.BigDecimal;

/**
 * The MerchantOrderRequest schema in contracts/openapi/parcel-service.v1.yaml. There is no merchant
 * id here and none is read from the body: the merchant is the one in the caller's token. The fee is
 * not accepted either; pricing-service sets it.
 *
 * @param merchantOrderRef the merchant's own order number, shown back in its order list
 * @param codAmount        cash to collect on delivery; it changes the fee but is not stored
 */
public record MerchantOrderRequest(
        @NotBlank @Size(max = Parcel.MAX_ORDER_REF_LENGTH) String merchantOrderRef,
        @NotNull @Valid AddressDto pickup,
        @NotNull @Valid AddressDto dropoff,
        @NotNull @Valid ContactDto recipient,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal weightKg,
        @NotNull SizeClass sizeClass,
        ServiceLevel serviceLevel,
        @DecimalMin("0") BigDecimal codAmount) {

    public MerchantOrderCommand toCommand() {
        return new MerchantOrderCommand(merchantOrderRef.strip(), pickup.toDomain(), dropoff.toDomain(),
                recipient.toDomain(), weightKg, sizeClass, serviceLevel, codAmount);
    }
}
