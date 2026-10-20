package lk.swiftdrop.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /** @return 1 if this is the first time the consumer sees the event, 0 for a duplicate */
    @Modifying
    @Query(value = """
            INSERT INTO processed_event (event_id, consumer, processed_at)
            VALUES (:eventId, :consumer, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("consumer") String consumer);
}
