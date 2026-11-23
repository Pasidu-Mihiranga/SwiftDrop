package lk.swiftdrop.parcel.domain.state;

import lk.swiftdrop.events.common.Types.ParcelStatus;

/**
 * The parcel lifecycle (design: parcel state diagram, M4):
 * <pre>
 * CREATED          -> PICKED_UP | CANCELLED
 * PICKED_UP        -> IN_TRANSIT
 * IN_TRANSIT       -> OUT_FOR_DELIVERY
 * OUT_FOR_DELIVERY -> DELIVERED | FAILED
 * FAILED           -> OUT_FOR_DELIVERY   (a re-attempt, decided by dispatch)
 * DELIVERED, CANCELLED: final
 * </pre>
 * The factory and the states are kept together so the whole graph reads in one place.
 */
public final class ParcelStates {
    private ParcelStates() { }

    public static ParcelState of(ParcelStatus status) {
        return switch (status) {
            case CREATED -> new Created();
            case PICKED_UP -> new PickedUp();
            case IN_TRANSIT -> new InTransit();
            case OUT_FOR_DELIVERY -> new OutForDelivery();
            case DELIVERED -> new Delivered();
            case FAILED -> new Failed();
            case CANCELLED -> new Cancelled();
        };
    }

    private abstract static class Base implements ParcelState {
        private final ParcelStatus status;
        Base(ParcelStatus status) { this.status = status; }
        @Override public ParcelStatus status() { return status; }
        @Override public ParcelState transitionTo(ParcelStatus target) { return reject(target); }
    }
    private static final class Created extends Base {
        Created() { super(ParcelStatus.CREATED); }
        @Override public ParcelState transitionTo(ParcelStatus target) {
            return target == ParcelStatus.PICKED_UP ? of(target) : target == ParcelStatus.CANCELLED ? of(target) : reject(target);
        }
    }
    private static final class PickedUp extends Base {
        PickedUp() { super(ParcelStatus.PICKED_UP); }
        @Override public ParcelState transitionTo(ParcelStatus target) { return target == ParcelStatus.IN_TRANSIT ? of(target) : reject(target); }
    }
    private static final class InTransit extends Base {
        InTransit() { super(ParcelStatus.IN_TRANSIT); }
        @Override public ParcelState transitionTo(ParcelStatus target) { return target == ParcelStatus.OUT_FOR_DELIVERY ? of(target) : reject(target); }
    }
    private static final class OutForDelivery extends Base {
        OutForDelivery() { super(ParcelStatus.OUT_FOR_DELIVERY); }
        @Override public ParcelState transitionTo(ParcelStatus target) {
            return target == ParcelStatus.DELIVERED || target == ParcelStatus.FAILED ? of(target) : reject(target);
        }
    }
    private static final class Failed extends Base {
        Failed() { super(ParcelStatus.FAILED); }
        @Override public ParcelState transitionTo(ParcelStatus target) { return target == ParcelStatus.OUT_FOR_DELIVERY ? of(target) : reject(target); }
    }
    private static final class Delivered extends Base { Delivered() { super(ParcelStatus.DELIVERED); } }
    private static final class Cancelled extends Base { Cancelled() { super(ParcelStatus.CANCELLED); } }
}
