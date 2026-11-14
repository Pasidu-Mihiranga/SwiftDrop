package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The monetary precision of every pricing amount: LKR with two decimals, rounded half up.
 *
 * <p>Rounding happens once per breakdown line, before that line feeds the next rule, so the
 * lines a customer is shown always add up to the total they are charged.
 */
public final class MonetaryRounding {

    public static final int SCALE = 2;
    public static final RoundingMode MODE = RoundingMode.HALF_UP;

    private MonetaryRounding() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, MODE);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }
}
