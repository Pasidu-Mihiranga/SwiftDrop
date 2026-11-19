-- Merchant onboarding (M7): one row per business application and its lifecycle.
--
-- owner_user_id is the Keycloak subject of the user who applied. It is a copied value, not a
-- foreign key: users live in Keycloak, not in this database.
--
-- The phone number and pickup address are personal data, so they are stored only as
-- AES-256-GCM ciphertext and are never readable in the database.
--
-- A rejected application does not block a new one: the uniqueness of the owner and of the
-- business registration number applies only to applications that are still in play.

CREATE TABLE merchant (
    id                  UUID          PRIMARY KEY,
    owner_user_id       VARCHAR(64)   NOT NULL,
    business_name       VARCHAR(150)  NOT NULL,
    -- normalised by the domain: upper case, no spaces, so "pv 1234" and "PV1234" collide
    br_number           VARCHAR(30)   NOT NULL,
    contact_email       VARCHAR(254)  NOT NULL,
    phone_enc           TEXT          NOT NULL,
    website             VARCHAR(255),
    pickup_address_enc  TEXT          NOT NULL,
    status              VARCHAR(10)   NOT NULL,
    -- the admin who made the latest decision (approve, reject or suspend)
    reviewed_by         VARCHAR(64),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    -- optimistic locking: two admins deciding at once cannot both succeed
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_merchant_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    -- every status other than PENDING is an admin decision, so it must say who and when
    CONSTRAINT ck_merchant_reviewed CHECK (
        status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    -- a reason belongs to a rejection and only to a rejection
    CONSTRAINT ck_merchant_rejection_reason CHECK (
        (status = 'REJECTED') = (rejection_reason IS NOT NULL))
);

-- One live application per user and per business registration number; this is what turns a
-- duplicate registration into a 409.
CREATE UNIQUE INDEX uq_merchant_owner_active ON merchant (owner_user_id) WHERE status <> 'REJECTED';
CREATE UNIQUE INDEX uq_merchant_br_number_active ON merchant (br_number) WHERE status <> 'REJECTED';

-- The admin approval queue: applications in one status, oldest first.
CREATE INDEX idx_merchant_status ON merchant (status, created_at);

-- "My merchant": the caller's latest application, including a rejected one.
CREATE INDEX idx_merchant_owner ON merchant (owner_user_id, created_at DESC);
