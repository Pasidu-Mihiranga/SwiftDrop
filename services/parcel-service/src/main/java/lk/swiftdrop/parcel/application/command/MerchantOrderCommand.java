package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.Recipient;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A merchant system's order, after validation. There is no merchant id here: the merchant is
 * always the one in the caller's token. The fee is not here either: pricing-service sets it.
 *
 * @param serviceLevel defaults to STANDARD, so leaving it out and sending STANDARD are the same order
 * @param codAmount    cash to collect; null means none and is stored as zero, so the two are the same order
 */
public record MerchantOrderCommand(String merchantOrderRef, Address pickup, Address dropoff, Recipient recipient,
                                   BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel,
                                   BigDecimal codAmount) {

    public MerchantOrderCommand {
        Objects.requireNonNull(merchantOrderRef, "merchantOrderRef");
        Objects.requireNonNull(pickup, "pickup");
        Objects.requireNonNull(dropoff, "dropoff");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(weightKg, "weightKg");
        Objects.requireNonNull(sizeClass, "sizeClass");
        serviceLevel = serviceLevel == null ? ServiceLevel.STANDARD : serviceLevel;
        codAmount = codAmount == null ? BigDecimal.ZERO : codAmount;
    }
}
