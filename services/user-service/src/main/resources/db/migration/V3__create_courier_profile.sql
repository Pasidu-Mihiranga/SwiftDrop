-- Dispatch details of a courier: vehicle, home zone and how many parcels they can take in a day.
-- Who is a courier is decided by Keycloak (realm role COURIER); this table only adds operational data.
-- home_zone_id is pricing-service's ZONE.id, copied by value: services never share foreign keys.

CREATE TABLE courier_profile (
    user_id            varchar(36) PRIMARY KEY REFERENCES user_profile (id) ON DELETE CASCADE,
    vehicle_type       varchar(20) NOT NULL CHECK (vehicle_type IN ('BIKE', 'THREE_WHEELER', 'VAN')),
    home_zone_id       uuid        NOT NULL,
    max_daily_capacity integer     NOT NULL CHECK (max_daily_capacity BETWEEN 1 AND 200),
    active             boolean     NOT NULL DEFAULT true,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0
);

CREATE INDEX ix_courier_profile_home_zone ON courier_profile (home_zone_id);
