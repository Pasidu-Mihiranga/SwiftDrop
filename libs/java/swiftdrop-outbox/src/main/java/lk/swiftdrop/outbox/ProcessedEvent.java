package lk.swiftdrop.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Marker that a consumer already handled an event (see {@link IdempotentConsumer}). */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @EmbeddedId
    private Key key;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // for JPA
    }

    public Key getKey() {
        return key;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Embeddable
    public record Key(@Column(name = "event_id") UUID eventId, @Column(name = "consumer") String consumer)
            implements Serializable {
    }
}
