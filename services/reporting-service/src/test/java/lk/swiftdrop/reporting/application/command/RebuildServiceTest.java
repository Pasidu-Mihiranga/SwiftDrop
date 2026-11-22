package lk.swiftdrop.reporting.application.command;

import lk.swiftdrop.reporting.application.port.out.EventReplay;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class RebuildServiceTest {

    @Test
    void clearsTheReadModelBeforeReplaying() {
        ProjectionStore store = mock(ProjectionStore.class);
        EventReplay replay = mock(EventReplay.class);

        new RebuildService(store, replay).rebuild();

        InOrder order = inOrder(store, replay);
        order.verify(store).clear();
        order.verify(replay).replayFromBeginning();
    }
}
