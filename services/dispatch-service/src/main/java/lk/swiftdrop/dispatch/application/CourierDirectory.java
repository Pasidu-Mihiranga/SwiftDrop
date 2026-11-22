package lk.swiftdrop.dispatch.application;
import java.util.List;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;
public interface CourierDirectory {
    List<Courier> couriers(String token);
}
