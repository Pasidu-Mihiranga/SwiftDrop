package lk.swiftdrop.reporting.application.port.out;

/** Re-reads the parcel topics from their earliest retained offset. */
public interface EventReplay {

    /** Asks every consumer to seek to the beginning; returns without waiting for the replay to finish. */
    void replayFromBeginning();
}
