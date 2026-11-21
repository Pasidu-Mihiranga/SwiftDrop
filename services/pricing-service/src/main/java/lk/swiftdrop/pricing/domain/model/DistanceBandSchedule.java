package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Checks a complete set of line-haul bands before it is published.
 *
 * <p>A quote must land in exactly one band, so the set has to cover every distance once: it
 * starts at 0 km, each band ends where the next starts, and only the last is open ended.
 */
public final class DistanceBandSchedule {

    private DistanceBandSchedule() {
    }

    /**
     * @return the bands ordered by distance
     * @throws IllegalArgumentException naming the first gap, overlap or misplaced open end
     */
    public static List<DistanceBand> requireComplete(List<DistanceBand> bands) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("Give at least one distance band");
        }
        List<DistanceBand> ordered = bands.stream().sorted(Comparator.comparing(DistanceBand::minKm)).toList();
        if (ordered.getFirst().minKm().signum() != 0) {
            throw new IllegalArgumentException("The first band must start at 0 km");
        }
        for (int i = 0; i < ordered.size() - 1; i++) {
            DistanceBand band = ordered.get(i);
            DistanceBand next = ordered.get(i + 1);
            if (band.maxKm() == null) {
                throw new IllegalArgumentException("Only the last band may be open ended");
            }
            if (band.maxKm().compareTo(next.minKm()) != 0) {
                throw new IllegalArgumentException("The band ending at " + km(band.maxKm())
                        + " km must be followed by one starting there, not at " + km(next.minKm()) + " km");
            }
        }
        if (ordered.getLast().maxKm() != null) {
            throw new IllegalArgumentException("The last band must be open ended so every distance is priced");
        }
        return ordered;
    }

    private static String km(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
