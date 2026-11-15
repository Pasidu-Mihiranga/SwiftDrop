-- Premium for covering the declared value of a parcel.
--
-- Cover is priced in bands rather than as a flat percentage, because the risk is not linear:
-- a small consignment carries a minimum handling cost whatever it is worth, and a valuable one
-- is charged on what it is worth. A band therefore has both a flat component and a percentage,
-- and the greater of the two is taken.
--
-- Bands are reachable only through the optional declaredValue field, so seeding real ones
-- changes no existing quote.

CREATE TABLE insurance_band (
    id UUID PRIMARY KEY,
    -- The top of the band. NULL is the open-ended last band.
    up_to_declared_value NUMERIC(12, 2),
    flat_fee NUMERIC(12, 2) NOT NULL,
    pct_of_declared_value NUMERIC(6, 5) NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_insurance_band
        UNIQUE NULLS NOT DISTINCT (up_to_declared_value, effective_from),
    CONSTRAINT ck_insurance_band_up_to_positive
        CHECK (up_to_declared_value IS NULL OR up_to_declared_value > 0),
    CONSTRAINT ck_insurance_band_flat_fee_nonnegative CHECK (flat_fee >= 0),
    CONSTRAINT ck_insurance_band_pct_is_fraction
        CHECK (pct_of_declared_value >= 0 AND pct_of_declared_value <= 1),
    CONSTRAINT ck_insurance_band_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_insurance_band_effective_from
    ON insurance_band (effective_from DESC, up_to_declared_value NULLS LAST);

INSERT INTO insurance_band (id, up_to_declared_value, flat_fee, pct_of_declared_value,
                            effective_from, effective_to)
VALUES
    (gen_random_uuid(),  10000.00,  60.00, 0.00000, DATE '2026-01-01', NULL),
    (gen_random_uuid(),  50000.00, 100.00, 0.01000, DATE '2026-01-01', NULL),
    (gen_random_uuid(), 200000.00, 250.00, 0.01500, DATE '2026-01-01', NULL),
    (gen_random_uuid(),      NULL, 500.00, 0.02000, DATE '2026-01-01', NULL)
ON CONFLICT DO NOTHING;
