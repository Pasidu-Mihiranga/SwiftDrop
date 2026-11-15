-- Tiered weight pricing for a lane.
--
-- A single per-kilogram rate charges the twentieth kilogram the same as the first, which no
-- courier does: the first kilogram carries the handling, and each one after it costs less to
-- move. A card may now carry bands instead.
--
-- Bands are marginal, not flat. A parcel is split across them and each band's kilograms are
-- charged at that band's rate, so the price rises smoothly instead of jumping at a boundary.
--
-- Tiers belong to a card rather than being versioned on their own. A tier is an attribute of
-- a lane's price, not a charge in its own right, so changing one closes the card and opens a
-- replacement exactly as any other price change does. Versioning them separately would let
-- the bands and the base fee drift apart in time, which cannot be priced.
--
-- No rows are seeded. A card with no bands charges its flat per_kg_fee for every kilogram,
-- which is what every one of the 81 seeded lanes keeps doing until an administrator says
-- otherwise.

CREATE TABLE rate_card_weight_tier (
    id UUID PRIMARY KEY,
    rate_card_id UUID NOT NULL REFERENCES rate_card (id) ON DELETE CASCADE,
    -- The top of the band. NULL is the open-ended last band.
    up_to_kg NUMERIC(6, 2),
    per_kg_fee NUMERIC(12, 2) NOT NULL,
    -- NULLS NOT DISTINCT so a card cannot have two open-ended bands, which would make the
    -- fee depend on row order. Postgres 15 and later.
    CONSTRAINT uq_rate_card_weight_tier UNIQUE NULLS NOT DISTINCT (rate_card_id, up_to_kg),
    CONSTRAINT ck_rate_card_weight_tier_up_to_positive CHECK (up_to_kg IS NULL OR up_to_kg > 0),
    CONSTRAINT ck_rate_card_weight_tier_fee_nonnegative CHECK (per_kg_fee >= 0)
);

CREATE INDEX idx_rate_card_weight_tier_card
    ON rate_card_weight_tier (rate_card_id, up_to_kg NULLS LAST);
