CREATE TABLE parcel (
    id UUID PRIMARY KEY,
    tracking_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id VARCHAR(255),
    merchant_id VARCHAR(255),
    courier_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE parcel_status_history (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcel(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    note VARCHAR(500),
    failure_reason VARCHAR(64),
    proof_type VARCHAR(32),
    proof_id VARCHAR(255),
    received_by VARCHAR(255),
    location_city VARCHAR(120),
    location_district VARCHAR(120),
    location_zone_code VARCHAR(80),
    changed_by VARCHAR(255) NOT NULL,
    changed_by_role VARCHAR(32) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE delivery_proof (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcel(id),
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX parcel_status_history_parcel_idx ON parcel_status_history(parcel_id, changed_at);
CREATE INDEX parcel_courier_idx ON parcel(courier_id);
