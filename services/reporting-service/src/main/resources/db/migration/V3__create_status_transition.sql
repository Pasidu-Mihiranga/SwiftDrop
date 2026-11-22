-- Append-only log of status changes. A parcel can fail more than once before it is delivered,
-- so the rollups count transitions, not parcels.
CREATE TABLE status_transition (
    event_id       UUID         PRIMARY KEY,
    parcel_id      UUID         NOT NULL,
    from_status    VARCHAR(24),
    to_status      VARCHAR(24)  NOT NULL,
    courier_id     VARCHAR(64),
    failure_reason VARCHAR(32),
    changed_at     TIMESTAMPTZ  NOT NULL,
    business_date  DATE         NOT NULL
);

CREATE INDEX ix_status_transition_date_status ON status_transition (business_date, to_status);
CREATE INDEX ix_status_transition_parcel ON status_transition (parcel_id);
