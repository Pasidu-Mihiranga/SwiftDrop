package lk.swiftdrop.parcel.domain.state;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static lk.swiftdrop.events.common.Types.ParcelStatus.CANCELLED;
import static lk.swiftdrop.events.common.Types.ParcelStatus.CREATED;
import static lk.swiftdrop.events.common.Types.ParcelStatus.DELIVERED;
import static lk.swiftdrop.events.common.Types.ParcelStatus.FAILED;
import static lk.swiftdrop.events.common.Types.ParcelStatus.IN_TRANSIT;
import static lk.swiftdrop.events.common.Types.ParcelStatus.OUT_FOR_DELIVERY;
import static lk.swiftdrop.events.common.Types.ParcelStatus.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every one of the 49 from/to pairs: the allowed ones succeed, every other one is refused. */
class ParcelStatesTest {

    private static final Map<ParcelStatus, Set<ParcelStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PICKED_UP, CANCELLED),
            PICKED_UP, EnumSet.of(IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, FAILED),
            FAILED, EnumSet.of(OUT_FOR_DELIVERY),
            DELIVERED, EnumSet.noneOf(ParcelStatus.class),
            CANCELLED, EnumSet.noneOf(ParcelStatus.class));

    static Stream<Arguments> allowed() {
        return pairs(true);
    }

    static Stream<Arguments> refused() {
        return pairs(false);
    }

    private static Stream<Arguments> pairs(boolean allowed) {
        return Stream.of(ParcelStatus.values()).flatMap(from -> Stream.of(ParcelStatus.values())
                .filter(to -> ALLOWED.get(from).contains(to) == allowed)
                .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allowed")
    void allowedStepsReachTheTarget(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelStates.of(from).transitionTo(to).status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @MethodSource("refused")
    void everyOtherStepIsRefusedWithAClearMessage(ParcelStatus from, ParcelStatus to) {
        assertThatThrownBy(() -> ParcelStates.of(from).transitionTo(to))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("A parcel cannot move from " + from + " to " + to + ".");
    }

    @ParameterizedTest
    @MethodSource("allowed")
    void theTableCoversEveryStatus(ParcelStatus from, ParcelStatus ignored) {
        assertThat(ALLOWED).containsKey(from);
        assertThat(ParcelStates.of(from).status()).isEqualTo(from);
    }
}
