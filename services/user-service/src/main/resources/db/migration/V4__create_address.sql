-- The address book: saved pickup and drop-off addresses of a person (shape of common.v1.yaml Address).
-- Street lines are personal data, so they are stored only as AES-GCM ciphertext (see PiiCipher).

CREATE TABLE address (
    id          uuid         PRIMARY KEY,
    user_id     varchar(36)  NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    label       varchar(40)  NOT NULL,
    line1_enc   varchar(512) NOT NULL,
    line2_enc   varchar(512),
    city        varchar(60)  NOT NULL,
    district    varchar(40)  NOT NULL,
    postal_code varchar(5)   CHECK (postal_code ~ '^[0-9]{5}$'),
    lat         numeric(9, 6) CHECK (lat BETWEEN -90 AND 90),
    lng         numeric(9, 6) CHECK (lng BETWEEN -180 AND 180),
    is_default  boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,
    CHECK ((lat IS NULL) = (lng IS NULL))
);

CREATE INDEX ix_address_user ON address (user_id, created_at);

-- At most one default address per person.
CREATE UNIQUE INDEX ux_address_one_default ON address (user_id) WHERE is_default;
