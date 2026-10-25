-- pricing-service (database: pricing_db): rate cards per ordered zone pair, versioned by effective date.
-- A card applies when effective_from <= quote date AND (effective_to IS NULL OR effective_to > quote date).
-- Cards are closed by setting effective_to; they are never deleted, so issued quotes stay auditable.

CREATE TABLE rate_card (
    id UUID PRIMARY KEY,
    from_zone_id UUID NOT NULL REFERENCES zone (id),
    to_zone_id UUID NOT NULL REFERENCES zone (id),
    base_fee NUMERIC(12, 2) NOT NULL,
    per_kg_fee NUMERIC(12, 2) NOT NULL,
    express_multiplier NUMERIC(5, 3) NOT NULL,
    cod_fee_pct NUMERIC(5, 4) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_rate_card_pair_effective_from UNIQUE (from_zone_id, to_zone_id, effective_from),
    CONSTRAINT ck_rate_card_base_fee_nonnegative CHECK (base_fee >= 0),
    CONSTRAINT ck_rate_card_per_kg_fee_nonnegative CHECK (per_kg_fee >= 0),
    CONSTRAINT ck_rate_card_express_multiplier_at_least_one CHECK (express_multiplier >= 1),
    CONSTRAINT ck_rate_card_cod_fee_pct_is_fraction CHECK (cod_fee_pct >= 0 AND cod_fee_pct <= 1),
    CONSTRAINT ck_rate_card_effective_range_ordered CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_rate_card_pair_effective_from ON rate_card (from_zone_id, to_zone_id, effective_from DESC);
