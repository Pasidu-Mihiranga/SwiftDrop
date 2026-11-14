package lk.swiftdrop.parcel.domain.state;

import lk.swiftdrop.events.common.Types.ParcelStatus;

/** Factory and concrete state implementations kept together to make the transition graph explicit. */
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
