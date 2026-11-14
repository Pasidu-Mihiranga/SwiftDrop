package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Comparator;

/**
 * The outside measurements of a parcel, in centimetres.
 *
 * <p>Used for two things: working out what the parcel displaces, and deciding which size class
 * it belongs to.
 */
public record Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {

    public Dimensions {
        require(lengthCm, "length");
        require(widthCm, "width");
        require(heightCm, "height");
    }

    private static void require(BigDecimal side, String name) {
        if (side == null || side.signum() <= 0) {
            throw new IllegalArgumentException("Parcel " + name + " must be positive");
        }
    }

    public static Dimensions of(String lengthCm, String widthCm, String heightCm) {
        return new Dimensions(new BigDecimal(lengthCm), new BigDecimal(widthCm), new BigDecimal(heightCm));
    }

    public BigDecimal volumeCm3() {
        return lengthCm.multiply(widthCm).multiply(heightCm);
    }

    /**
     * Whether a parcel of these measurements fits inside the given limit.
     *
     * <p>The comparison is rotation tolerant: both sets of sides are sorted longest first and
     * compared in order, so a 40x20x30 parcel fits a 40x30x20 limit. Comparing the sides as
     * named would reject a parcel that plainly goes in the box, which is a real complaint and
     * an avoidable one.
     */
    public boolean fitsWithin(Dimensions limit) {
        if (limit == null) {
            return false;
        }
        List<BigDecimal> parcel = sortedSides();
        List<BigDecimal> allowed = limit.sortedSides();
        for (int side = 0; side < 3; side++) {
            if (parcel.get(side).compareTo(allowed.get(side)) > 0) {
                return false;
            }
        }
        return true;
    }

    /** The display form used by the published size-class schema, for example {@code 40x30x20}. */
    public String label() {
        return plain(lengthCm) + "x" + plain(widthCm) + "x" + plain(heightCm);
    }

    private static String plain(BigDecimal side) {
        return side.stripTrailingZeros().toPlainString();
    }

    private List<BigDecimal> sortedSides() {
        return java.util.stream.Stream.of(lengthCm, widthCm, heightCm)
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
