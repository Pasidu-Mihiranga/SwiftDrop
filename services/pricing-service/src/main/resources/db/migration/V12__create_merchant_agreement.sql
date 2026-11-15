-- Negotiated rates for a merchant.
--
-- A merchant shipping a thousand parcels a month does not pay the counter price. Rather than
-- giving each one its own copy of all 81 lanes, an agreement sits over the standard cards: a
-- discount on the delivery charge, optional per-lane overrides for the routes actually
-- negotiated, and the right to waive particular surcharges. A change to a standard rate then
-- still reaches every merchant, which is the whole point of keeping one set of cards.
--
-- merchant_id is an opaque UUID taken from the merchant_id claim on the caller's token. There
-- is no foreign key: merchant-service owns the merchant in its own database, and reaching
-- across that boundary is what database-per-service exists to prevent.
--
-- Versioned like a rate card, so an agreement that has expired can still explain a quote
-- issued while it was in force.

CREATE TABLE merchant_agreement (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    discount_pct NUMERIC(5, 4) NOT NULL DEFAULT 0,
    discount_flat NUMERIC(12, 2) NOT NULL DEFAULT 0,
    waives_remote_area BOOLEAN NOT NULL DEFAULT FALSE,
    waives_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    waives_cod_fee BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    CONSTRAINT uq_merchant_agreement UNIQUE (merchant_id, effective_from),
    CONSTRAINT ck_merchant_agreement_discount_pct_is_fraction
        CHECK (discount_pct >= 0 AND discount_pct <= 1),
    CONSTRAINT ck_merchant_agreement_discount_flat_nonnegative CHECK (discount_flat >= 0),
    CONSTRAINT ck_merchant_agreement_effective_range_ordered
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_merchant_agreement_merchant
    ON merchant_agreement (merchant_id, effective_from DESC);

-- A lane the merchant negotiated separately. A flat base fee replaces the card's; a discount
-- here replaces the agreement's for that lane only.
CREATE TABLE merchant_agreement_lane_override (
    id UUID PRIMARY KEY,
    agreement_id UUID NOT NULL REFERENCES merchant_agreement (id) ON DELETE CASCADE,
    from_zone_id UUID NOT NULL REFERENCES zone (id),
    to_zone_id UUID NOT NULL REFERENCES zone (id),
    discount_pct NUMERIC(5, 4),
    flat_base_fee NUMERIC(12, 2),
    CONSTRAINT uq_merchant_agreement_lane_override
        UNIQUE (agreement_id, from_zone_id, to_zone_id),
    CONSTRAINT ck_merchant_lane_override_discount_pct_is_fraction
        CHECK (discount_pct IS NULL OR (discount_pct >= 0 AND discount_pct <= 1)),
    CONSTRAINT ck_merchant_lane_override_flat_base_fee_nonnegative
        CHECK (flat_base_fee IS NULL OR flat_base_fee >= 0),
    -- An override that overrides nothing is a row somebody forgot to finish.
    CONSTRAINT ck_merchant_lane_override_changes_something
        CHECK (discount_pct IS NOT NULL OR flat_base_fee IS NOT NULL)
);

CREATE INDEX idx_merchant_lane_override_agreement
    ON merchant_agreement_lane_override (agreement_id);
