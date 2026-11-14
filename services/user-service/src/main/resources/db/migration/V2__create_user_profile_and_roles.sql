-- Profiles of Keycloak users. Keycloak owns identity and credentials; this table owns profile data.
-- user_profile.id is the Keycloak subject (JWT "sub"), so a row is created just in time on the
-- user's first GET /api/v1/users/me and no password is ever stored here.

CREATE TABLE role (
    code        varchar(20)  PRIMARY KEY,
    description varchar(120) NOT NULL
);

INSERT INTO role (code, description) VALUES
    ('CUSTOMER',   'Books and tracks deliveries'),
    ('MERCHANT',   'Sends orders from an online store'),
    ('COURIER',    'Picks up and delivers parcels'),
    ('DISPATCHER', 'Assigns parcels to couriers'),
    ('ADMIN',      'Manages users, merchants and pricing');

CREATE TABLE user_profile (
    id         varchar(36)  PRIMARY KEY,
    email      varchar(254) NOT NULL,
    full_name  varchar(100) NOT NULL,
    -- AES-GCM ciphertext of the E.164 phone number (see PiiCipher); never stored in plain text
    phone_enc  varchar(512),
    status     varchar(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_user_profile_email ON user_profile (lower(email));

-- Mirror of the user's Keycloak realm roles, so queries such as "all couriers" need no Keycloak call.
CREATE TABLE user_role (
    user_id   varchar(36) NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    role_code varchar(20) NOT NULL REFERENCES role (code),
    synced_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX ix_user_role_role_code ON user_role (role_code);
