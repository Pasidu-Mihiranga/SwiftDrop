-- pricing-service (database: pricing_db): parcel size classes and their fee multipliers.
-- The four codes are fixed by the shared SizeClass contract; limits and multipliers are configuration.

CREATE TABLE size_class (
    code VARCHAR(4) PRIMARY KEY,
    max_weight_kg NUMERIC(6, 2) NOT NULL,
    max_dimensions_cm VARCHAR(32) NOT NULL,
    multiplier NUMERIC(5, 3) NOT NULL,
    CONSTRAINT ck_size_class_code_known CHECK (code IN ('S', 'M', 'L', 'XL')),
    CONSTRAINT ck_size_class_max_weight_kg_positive CHECK (max_weight_kg > 0),
    CONSTRAINT ck_size_class_max_dimensions_cm_not_blank CHECK (btrim(max_dimensions_cm) <> ''),
    CONSTRAINT ck_size_class_multiplier_at_least_one CHECK (multiplier >= 1)
);
