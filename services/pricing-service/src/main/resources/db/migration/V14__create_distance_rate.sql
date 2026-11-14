-- What a same-day job costs per kilometre.
--
-- Zones are too coarse for local work: a run across Colombo and a run to the far side of the
-- district are the same lane and the same price, which is wrong in both directions. A same-day
-- delivery is therefore priced on the road distance actually travelled.
--
-- min_chargeable_km is what makes an intra-city job sane. Two points in the same district can
-- be a few hundred metres apart, and a job is not free because it is short: the courier still
-- has to go and get it.
--
-- road_factor converts a straight line into a plausible road distance, for when no routing
-- engine is reachable. It is data rather than a constant because, like every other fee
-- literal, it belongs in a migration.

CREATE TABLE distance_rate (
    id UUID PRIMARY KEY,
    flag_fall NUMERIC(12, 2) NOT NULL,
    per_km_fee NUMERIC(12, 2) NOT NULL,
    min_chargeable_km NUMERIC(8, 2) NOT NULL DEFAULT 0,
    road_factor NUMERIC(5, 3) NOT NULL DEFAULT 1.300,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_distance_rate_effective_from UNIQUE (effective_from),
    CONSTRAINT ck_distance_rate_flag_fall_nonnegative CHECK (flag_fall >= 0),
    CONSTRAINT ck_distance_rate_per_km_fee_nonnegative CHECK (per_km_fee >= 0),
    CONSTRAINT ck_distance_rate_min_km_nonnegative CHECK (min_chargeable_km >= 0),
    CONSTRAINT ck_distance_rate_road_factor_at_least_one CHECK (road_factor >= 1),
    CONSTRAINT ck_distance_rate_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

INSERT INTO distance_rate (id, flag_fall, per_km_fee, min_chargeable_km, road_factor,
                           effective_from, effective_to)
VALUES (gen_random_uuid(), 300.00, 55.00, 3.00, 1.300, DATE '2026-01-01', NULL)
ON CONFLICT (effective_from) DO NOTHING;
