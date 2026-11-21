-- Distributed tracing (M9): the W3C traceparent of the request that wrote the event, so the outbox
-- relay can put it on the Kafka record and consumers continue the same trace.
-- Repeatable and idempotent: every service database is already past its own V1..Vn, so a shared
-- versioned migration here would be refused as out of order.
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(55);
