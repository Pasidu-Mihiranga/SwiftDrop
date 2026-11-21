package lk.swiftdrop.merchant.domain.exception;

import lk.swiftdrop.common.web.error.ConflictException;

/**
 * 409: another application that has not been rejected already uses this business registration
 * number. The number itself is not repeated in the message.
 */
public class BrNumberAlreadyRegisteredException extends ConflictException {

    public static final String CODE = "MERCHANT_BR_NUMBER_TAKEN";

    public BrNumberAlreadyRegisteredException() {
        super(CODE, "A business with this registration number has already applied or is already a merchant.");
    }
}
