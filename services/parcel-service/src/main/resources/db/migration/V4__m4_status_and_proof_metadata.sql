-- M4 courier status metadata. Existing M3 history columns are preserved.
ALTER TABLE parcel_status_history ADD COLUMN IF NOT EXISTS proof_type VARCHAR(20);
ALTER TABLE parcel_status_history ADD COLUMN IF NOT EXISTS received_by VARCHAR(100);

CREATE TABLE delivery_proof (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcel(id) ON DELETE CASCADE,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX delivery_proof_parcel_idx ON delivery_proof(parcel_id, created_at DESC);
