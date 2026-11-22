package lk.swiftdrop.dispatch.application;

import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import java.time.*;
import java.util.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;

public interface DispatchStore {
    void created(ParcelCreated event);
    void status(ParcelStatusChanged event);
    Parcel lock(UUID parcelId);
    Parcel lockAssignment(UUID assignmentId);
    Page unassigned(String zone, int page, int size);
    List<Parcel> assignments(LocalDate date, String courier);
    Map<String, Counts> counts(LocalDate today, ZoneId zone);
    Allocation save(Parcel parcel, String courier, LocalDate date, String actor, Instant now);
}
