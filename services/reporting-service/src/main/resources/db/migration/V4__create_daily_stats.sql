-- Daily rollups served by the report endpoints. They are derived data: every row is recomputed
-- from parcel_fact and status_transition for the days an event touches, never incremented.
CREATE TABLE daily_delivery_stats (
    business_date DATE        NOT NULL,
    zone_code     VARCHAR(32) NOT NULL,                -- 'UNKNOWN' until the parcel's ParcelCreated arrives
    created       INTEGER     NOT NULL DEFAULT 0,
    delivered     INTEGER     NOT NULL DEFAULT 0,
    failed        INTEGER     NOT NULL DEFAULT 0,
    cancelled     INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (business_date, zone_code)
);

CREATE TABLE courier_daily_stats (
    business_date DATE        NOT NULL,
    courier_id    VARCHAR(64) NOT NULL,
    delivered     INTEGER     NOT NULL DEFAULT 0,
    failed        INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (business_date, courier_id)
);
