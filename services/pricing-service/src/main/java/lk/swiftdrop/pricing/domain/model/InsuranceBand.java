package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * The premium charged for covering a parcel worth up to a given amount.
 *
 * <p>The premium is the greater of the flat fee and the percentage, not their sum: the flat fee
 * is the minimum worth writing cover for, and the percentage takes over once the consignment is
 * valuable enough to justify it.
 *
 * @param upToDeclaredValue the top of the band; null is the open-ended last band
 */
public record InsuranceBand(BigDecimal upToDeclaredValue, BigDecimal flatFee,
                            BigDecimal pctOfDeclaredValue) {

    public InsuranceBand {
        if (upToDeclaredValue != null && upToDeclaredValue.signum() <= 0) {
            throw new IllegalArgumentException("Insurance band limit must be positive");
        }
        if (flatFee == null || flatFee.signum() < 0) {
            throw new IllegalArgumentException("Insurance flat fee must not be negative");
        }
        if (pctOfDeclaredValue == null || pctOfDeclaredValue.signum() < 0
                || pctOfDeclaredValue.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Insurance percentage must be a fraction of one");
        }
    }

    public boolean covers(BigDecimal declaredValue) {
        return upToDeclaredValue == null || declaredValue.compareTo(upToDeclaredValue) <= 0;
    }

    public BigDecimal premiumFor(BigDecimal declaredValue) {
        return flatFee.max(declaredValue.multiply(pctOfDeclaredValue));
    }
}
