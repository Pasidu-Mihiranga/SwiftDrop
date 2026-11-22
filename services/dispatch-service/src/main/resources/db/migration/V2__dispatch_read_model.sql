CREATE TABLE parcel_summary (
 parcel_id UUID PRIMARY KEY, tracking_number VARCHAR(64), zone VARCHAR(100), size_class VARCHAR(10),
 pickup_city VARCHAR(200), dropoff_city VARCHAR(200), created_at TIMESTAMPTZ,
 status VARCHAR(32) NOT NULL DEFAULT 'CREATED', status_at TIMESTAMPTZ,
 booked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX parcel_summary_waiting ON parcel_summary(zone, created_at, parcel_id) WHERE booked;
CREATE TABLE assignment (
 id UUID PRIMARY KEY, parcel_id UUID NOT NULL UNIQUE REFERENCES parcel_summary(parcel_id),
 courier_id VARCHAR(255) NOT NULL, previous_courier_id VARCHAR(255), run_date DATE NOT NULL,
 assigned_by VARCHAR(255) NOT NULL, assigned_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX assignment_run ON assignment(courier_id, run_date);
-- Derive counters from the current parcel projection: no increment drift on replay or reassignment.
-- Date bucketing is applied by the query using the configured business timezone.
CREATE VIEW courier_workload AS
 SELECT a.courier_id, p.parcel_id, p.status, p.status_at
 FROM assignment a JOIN parcel_summary p ON p.parcel_id = a.parcel_id;
