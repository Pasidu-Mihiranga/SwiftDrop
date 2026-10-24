CREATE TABLE zone (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_zone_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_zone_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE district_zone (
    district VARCHAR(100) PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES zone (id),
    CONSTRAINT ck_district_zone_district_not_blank CHECK (btrim(district) <> '')
);

CREATE INDEX idx_district_zone_zone_id ON district_zone (zone_id);
