package lk.swiftdrop.reporting.application.command;

import lk.swiftdrop.reporting.application.port.out.EventReplay;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Rebuilds the read model from the event log: empty it, then replay the topics from the start.
 *
 * <p>Safe while events keep arriving. An event applied between the clear and the seek is recorded as
 * processed, so its replayed copy is skipped as a duplicate; and because rollups are recomputed from
 * the facts, the order the replay delivers events in does not matter. The result is only complete
 * for events the topics still retain.
 */
@Service
public class RebuildService {

    private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

    private final ProjectionStore store;
    private final EventReplay replay;

    public RebuildService(ProjectionStore store, EventReplay replay) {
        this.store = store;
        this.replay = replay;
    }

    public void rebuild() {
        store.clear();
        replay.replayFromBeginning();
        log.info("Reporting projections cleared; replaying parcel topics from the beginning");
    }
}
