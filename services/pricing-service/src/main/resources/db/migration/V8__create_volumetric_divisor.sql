-- The divisor that converts a parcel's volume into a billable weight.
--
-- A parcel is charged on the greater of what it weighs and what it displaces, because a van
-- fills up by volume long before it reaches its weight limit. Volumetric weight in kilograms
-- is length x width x height in centimetres, divided by this number; 5000 is the long-standing
-- courier convention for road and air freight.
--
-- It is a table rather than a configuration key because it is a price. Changing it reprices
-- every bulky parcel in the country, so it is versioned exactly like a rate card: closed and
-- replaced, never edited, with a half-open effective period. A quote issued last month can
-- then still be explained from the data that produced it.
--
-- When no row is in force the volumetric step is skipped and a parcel is charged on its actual
-- weight, which is the same answer the service gave before this existed.

CREATE TABLE volumetric_divisor (
    id UUID PRIMARY KEY,
    divisor NUMERIC(8, 1) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_volumetric_divisor_effective_from UNIQUE (effective_from),
    CONSTRAINT ck_volumetric_divisor_positive CHECK (divisor > 0),
    CONSTRAINT ck_volumetric_divisor_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_volumetric_divisor_effective_from
    ON volumetric_divisor (effective_from DESC);

INSERT INTO volumetric_divisor (id, divisor, effective_from, effective_to)
VALUES (gen_random_uuid(), 5000.0, DATE '2026-01-01', NULL)
ON CONFLICT (effective_from) DO NOTHING;
