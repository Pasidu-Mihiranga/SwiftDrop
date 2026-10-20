-- Idempotent consumer: one row per (event, consumer). Kafka delivers at-least-once,
-- so a consumer skips any event id it has already processed.
CREATE TABLE processed_event (
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
