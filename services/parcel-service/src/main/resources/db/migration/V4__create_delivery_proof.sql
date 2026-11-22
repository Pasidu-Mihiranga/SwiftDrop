-- Courier operations (M4): proof-of-delivery files. The photo or signature itself lives in
-- object storage under a random key; this row records which parcel it belongs to, so a status
-- update can refer to it by id, and when it was used to prove a delivery.
-- (parcel_status_history already has failure_reason, proof_id, lat and lng from V2.)
CREATE TABLE delivery_proof (
    id           UUID          PRIMARY KEY,
    parcel_id    UUID          NOT NULL REFERENCES parcel (id) ON DELETE CASCADE,
    object_key   VARCHAR(512)  NOT NULL,
    content_type VARCHAR(64)   NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    used_at      TIMESTAMPTZ,
    CONSTRAINT uq_delivery_proof_object_key UNIQUE (object_key),
    CONSTRAINT ck_delivery_proof_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_delivery_proof_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE INDEX idx_delivery_proof_parcel ON delivery_proof (parcel_id);
