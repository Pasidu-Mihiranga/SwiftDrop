-- Extra cost of collecting from or delivering to a hard-to-reach district.
--
-- Zones price the distance between provinces. They say nothing about the last mile, which is
-- where the cost actually varies: a delivery in Mullaitivu is not a Colombo delivery with a
-- longer drive in front of it, it is a round trip a courier makes rarely and partly empty.
-- The surcharge is therefore keyed on district, not zone.
--
-- Collection and delivery are charged independently and summed into one breakdown line: they
-- are two separate difficulties, but one rule contributes one line, and that invariant is
-- worth more than the extra detail.
--
-- No rows are seeded. Until an administrator marks a district remote, no quote changes.

CREATE TABLE remote_area_surcharge (
    id UUID PRIMARY KEY,
    district VARCHAR(100) NOT NULL,
    flat_fee NUMERIC(12, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_remote_area_surcharge UNIQUE (district, effective_from),
    CONSTRAINT ck_remote_area_surcharge_fee_nonnegative CHECK (flat_fee >= 0),
    CONSTRAINT ck_remote_area_surcharge_district_not_blank CHECK (btrim(district) <> ''),
    CONSTRAINT ck_remote_area_surcharge_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_remote_area_surcharge_district
    ON remote_area_surcharge (district, effective_from DESC);
