-- Line haul by distance band, the fee-wide settings (fuel, tax, minimum charge), special
-- handling options, and the vehicles a same-day job can be priced for.
--
-- PLACEHOLDER VALUES. Every amount and percentage seeded below is a starting point chosen so
-- the feature can be demonstrated end to end, not an agreed SwiftDrop tariff. Review them
-- before release and change them in a new migration; nothing in Java or the web app repeats
-- them.

-- One row in force at a time, versioned like a rate card: close it and add a successor rather
-- than editing it, so the configuration behind an older quote can still be identified.
CREATE TABLE pricing_setting (
    id UUID PRIMARY KEY,
    fuel_surcharge_pct NUMERIC(6, 4) NOT NULL,
    tax_pct NUMERIC(6, 4) NOT NULL,
    minimum_charge NUMERIC(12, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_pricing_setting_effective_from UNIQUE (effective_from),
    CONSTRAINT ck_pricing_setting_fuel_pct_range CHECK (fuel_surcharge_pct >= 0 AND fuel_surcharge_pct < 1),
    CONSTRAINT ck_pricing_setting_tax_pct_range CHECK (tax_pct >= 0 AND tax_pct < 1),
    CONSTRAINT ck_pricing_setting_minimum_nonnegative CHECK (minimum_charge >= 0),
    CONSTRAINT ck_pricing_setting_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- Road distance between pickup and drop-off, banded. Each band carries the line-haul charge
-- and the transit time it implies. Bands are half-open, [min_km, max_km); the last is open
-- ended. Same-day work is priced per kilometre instead (distance_rate) and ignores bands.
CREATE TABLE distance_band (
    id UUID PRIMARY KEY,
    min_km NUMERIC(8, 2) NOT NULL,
    max_km NUMERIC(8, 2),
    line_haul_fee NUMERIC(12, 2) NOT NULL,
    standard_transit_days SMALLINT NOT NULL,
    express_transit_days SMALLINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_distance_band_start UNIQUE (min_km, effective_from),
    CONSTRAINT ck_distance_band_min_nonnegative CHECK (min_km >= 0),
    CONSTRAINT ck_distance_band_range_ordered CHECK (max_km IS NULL OR max_km > min_km),
    CONSTRAINT ck_distance_band_fee_nonnegative CHECK (line_haul_fee >= 0),
    CONSTRAINT ck_distance_band_transit_positive
        CHECK (standard_transit_days > 0 AND express_transit_days > 0
               AND express_transit_days <= standard_transit_days),
    CONSTRAINT ck_distance_band_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE special_handling (
    code VARCHAR(32) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    fee NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_special_handling_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_special_handling_label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT ck_special_handling_fee_nonnegative CHECK (fee >= 0)
);

-- What each vehicle can take on a same-day job, and how its per-kilometre price compares with
-- the base distance rate. A job outside a vehicle's limits is refused, not silently upgraded.
CREATE TABLE same_day_vehicle (
    code VARCHAR(16) PRIMARY KEY,
    label VARCHAR(60) NOT NULL,
    max_weight_kg NUMERIC(6, 2) NOT NULL,
    max_distance_km NUMERIC(8, 2) NOT NULL,
    rate_multiplier NUMERIC(5, 3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_same_day_vehicle_code CHECK (code IN ('BIKE', 'VAN')),
    CONSTRAINT ck_same_day_vehicle_weight_positive CHECK (max_weight_kg > 0),
    CONSTRAINT ck_same_day_vehicle_distance_positive CHECK (max_distance_km > 0),
    CONSTRAINT ck_same_day_vehicle_multiplier_positive CHECK (rate_multiplier > 0)
);

-- PLACEHOLDER: 5% fuel, 18% tax (the standard VAT rate at the time of writing), LKR 350 minimum.
INSERT INTO pricing_setting (id, fuel_surcharge_pct, tax_pct, minimum_charge, effective_from)
VALUES (gen_random_uuid(), 0.0500, 0.1800, 350.00, DATE '2026-01-01')
ON CONFLICT (effective_from) DO NOTHING;

-- PLACEHOLDER: the zone rate card still carries the lane; line haul adds a distance-sensitive
-- charge on top, which is zero for short local runs.
INSERT INTO distance_band (id, min_km, max_km, line_haul_fee, standard_transit_days,
                           express_transit_days, effective_from)
VALUES (gen_random_uuid(),   0.00,  30.00,   0.00, 1, 1, DATE '2026-01-01'),
       (gen_random_uuid(),  30.00, 100.00, 150.00, 2, 1, DATE '2026-01-01'),
       (gen_random_uuid(), 100.00, 200.00, 300.00, 2, 1, DATE '2026-01-01'),
       (gen_random_uuid(), 200.00, 300.00, 450.00, 3, 2, DATE '2026-01-01'),
       (gen_random_uuid(), 300.00,   NULL, 600.00, 4, 2, DATE '2026-01-01')
ON CONFLICT (min_km, effective_from) DO NOTHING;

-- PLACEHOLDER fees.
INSERT INTO special_handling (code, label, fee, sort_order)
VALUES ('FRAGILE',    'Fragile',                     250.00, 10),
       ('PERISHABLE', 'Perishable (keep cool)',      300.00, 20),
       ('LIQUID',     'Contains liquid',             200.00, 30),
       ('SIGNATURE',  'Signature required on delivery', 100.00, 40)
ON CONFLICT (code) DO NOTHING;

-- PLACEHOLDER limits. The van's weight limit matches the quote endpoint's 100 kg ceiling.
INSERT INTO same_day_vehicle (code, label, max_weight_kg, max_distance_km, rate_multiplier)
VALUES ('BIKE', 'Bike', 10.00,  40.00, 1.000),
       ('VAN',  'Van', 100.00, 120.00, 1.800)
ON CONFLICT (code) DO NOTHING;
