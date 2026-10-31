-- One row per parcel, filled in by whichever event arrives first. Kafka orders events per topic
-- partition only, so a status change or an assignment can be seen before ParcelCreated; the
-- columns that event does not know about simply stay NULL until it arrives.
CREATE TABLE parcel_fact (
    parcel_id           UUID         PRIMARY KEY,
    tracking_number     VARCHAR(32),
    zone_code           VARCHAR(32),               -- dropoff zone; NULL until ParcelCreated is seen
    source              VARCHAR(16),
    merchant_id         VARCHAR(64),
    created_at          TIMESTAMPTZ,
    created_date        DATE,                      -- business day of created_at (swiftdrop.reporting.time-zone)
    courier_id          VARCHAR(64),               -- latest assignment
    courier_assigned_at TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_parcel_fact_created_date ON parcel_fact (created_date);
