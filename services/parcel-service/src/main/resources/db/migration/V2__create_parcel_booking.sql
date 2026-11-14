-- Customer booking (M3): the parcel aggregate, its append-only status history and the
-- idempotency record used by the merchant order API.
-- Customer, merchant, courier, zone and quote identifiers are copied values, not foreign keys:
-- they belong to other services, and no other service joins these tables.

CREATE TABLE parcel (
    id                    UUID          PRIMARY KEY,
    tracking_number       VARCHAR(22)   NOT NULL,
    source                VARCHAR(10)   NOT NULL,
    customer_id           VARCHAR(64),
    merchant_id           VARCHAR(64),
    merchant_order_ref    VARCHAR(100),
    -- AES-256-GCM ciphertext of the address as JSON; never readable in the database
    pickup_address_enc    TEXT          NOT NULL,
    dropoff_address_enc   TEXT          NOT NULL,
    pickup_zone           VARCHAR(20),
    dropoff_zone          VARCHAR(20),
    recipient_name        VARCHAR(100)  NOT NULL,
    recipient_phone_enc   TEXT          NOT NULL,
    recipient_email_enc   TEXT,
    weight_kg             NUMERIC(6, 2) NOT NULL,
    size_class            VARCHAR(4)    NOT NULL,
    service_level         VARCHAR(10)   NOT NULL,
    fee                   NUMERIC(12, 2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    quote_id              UUID,
    notes                 VARCHAR(300),
    status                VARCHAR(20)   NOT NULL,
    courier_id            VARCHAR(64),
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    -- optimistic locking: two concurrent changes cannot both succeed
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_parcel_tracking_number UNIQUE (tracking_number),
    CONSTRAINT ck_parcel_source CHECK (source IN ('CUSTOMER', 'MERCHANT')),
    CONSTRAINT ck_parcel_status CHECK (status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_parcel_weight CHECK (weight_kg > 0),
    CONSTRAINT ck_parcel_fee CHECK (fee >= 0)
);

-- "My parcels": newest first, optionally filtered by status.
CREATE INDEX idx_parcel_customer ON parcel (customer_id, created_at DESC);
CREATE INDEX idx_parcel_merchant ON parcel (merchant_id, created_at DESC) WHERE merchant_id IS NOT NULL;
CREATE INDEX idx_parcel_courier ON parcel (courier_id) WHERE courier_id IS NOT NULL;

CREATE TABLE parcel_status_history (
    id             UUID         PRIMARY KEY,
    parcel_id      UUID         NOT NULL REFERENCES parcel (id) ON DELETE CASCADE,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)  NOT NULL,
    actor_id       VARCHAR(64)  NOT NULL,
    actor_role     VARCHAR(20)  NOT NULL,
    note           VARCHAR(500),
    failure_reason VARCHAR(30),
    proof_id       VARCHAR(64),
    lat            NUMERIC(9, 6),
    lng            NUMERIC(9, 6),
    occurred_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_parcel_status_history_parcel ON parcel_status_history (parcel_id, occurred_at);

-- Merchant order API: a repeated request with the same Idempotency-Key returns the first result.
CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(100) NOT NULL,
    merchant_id     VARCHAR(64)  NOT NULL,
    parcel_id       UUID         NOT NULL REFERENCES parcel (id) ON DELETE CASCADE,
    request_hash    VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (merchant_id, idempotency_key)
);
