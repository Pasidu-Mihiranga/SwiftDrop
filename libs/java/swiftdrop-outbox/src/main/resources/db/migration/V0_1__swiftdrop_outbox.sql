-- Shipped by libs/java/swiftdrop-outbox. Applied automatically by Flyway in every service that
-- depends on the library (version 0.x runs before the service's own V1+ migrations).

-- Transactional outbox: written in the same DB transaction as the business change,
-- published to Kafka afterwards by OutboxRelay.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_id   VARCHAR(64)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    message_key    VARCHAR(200) NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

-- The relay only ever scans unpublished rows, oldest first.
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
