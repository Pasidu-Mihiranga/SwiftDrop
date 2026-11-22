-- The record of a merchant's OAuth 2.0 client in Keycloak (client credentials).
--
-- No client secret is stored here or anywhere else in this service: Keycloak holds it, and the
-- merchant sees it once, when it is issued or rotated. This table only remembers which client
-- belongs to which merchant and when its secret last changed.

CREATE TABLE api_credential (
    id                  UUID          PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchant (id),
    keycloak_client_id  VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    rotated_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    CONSTRAINT uq_api_credential_client UNIQUE (keycloak_client_id),
    CONSTRAINT ck_api_credential_rotated_after_created CHECK (rotated_at IS NULL OR rotated_at >= created_at),
    CONSTRAINT ck_api_credential_revoked_after_created CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

-- A merchant has at most one client that has not been revoked.
CREATE UNIQUE INDEX uq_api_credential_merchant_active ON api_credential (merchant_id) WHERE revoked_at IS NULL;
