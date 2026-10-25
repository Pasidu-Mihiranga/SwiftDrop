-- pricing-service (database: pricing_db): reviewed zone and district reference data.
-- This file is the authoritative mapping of all 25 Sri Lankan districts to service zones.
-- Zones follow the provinces. Zone identifiers are fixed literals so that rate-card seed data
-- and every environment refer to the same rows. Never duplicate this mapping in Java or
-- frontend code; read it from GET /api/v1/pricing/zones instead.

INSERT INTO zone (id, code, name, active) VALUES
    ('00000000-0000-4000-8000-000000000001', 'WP',  'Western',       TRUE),
    ('00000000-0000-4000-8000-000000000002', 'CP',  'Central',       TRUE),
    ('00000000-0000-4000-8000-000000000003', 'SP',  'Southern',      TRUE),
    ('00000000-0000-4000-8000-000000000004', 'NP',  'Northern',      TRUE),
    ('00000000-0000-4000-8000-000000000005', 'EP',  'Eastern',       TRUE),
    ('00000000-0000-4000-8000-000000000006', 'NWP', 'North Western', TRUE),
    ('00000000-0000-4000-8000-000000000007', 'NCP', 'North Central', TRUE),
    ('00000000-0000-4000-8000-000000000008', 'UP',  'Uva',           TRUE),
    ('00000000-0000-4000-8000-000000000009', 'SGP', 'Sabaragamuwa',  TRUE)
ON CONFLICT (code) DO NOTHING;

-- All 25 districts. The zone is resolved by code so the mapping stays readable in review.
INSERT INTO district_zone (district, zone_id)
SELECT d.district, z.id
FROM (VALUES
    ('Colombo',      'WP'),
    ('Gampaha',      'WP'),
    ('Kalutara',     'WP'),
    ('Kandy',        'CP'),
    ('Matale',       'CP'),
    ('Nuwara Eliya', 'CP'),
    ('Galle',        'SP'),
    ('Matara',       'SP'),
    ('Hambantota',   'SP'),
    ('Jaffna',       'NP'),
    ('Kilinochchi',  'NP'),
    ('Mannar',       'NP'),
    ('Mullaitivu',   'NP'),
    ('Vavuniya',     'NP'),
    ('Ampara',       'EP'),
    ('Batticaloa',   'EP'),
    ('Trincomalee',  'EP'),
    ('Kurunegala',   'NWP'),
    ('Puttalam',     'NWP'),
    ('Anuradhapura', 'NCP'),
    ('Polonnaruwa',  'NCP'),
    ('Badulla',      'UP'),
    ('Monaragala',   'UP'),
    ('Kegalle',      'SGP'),
    ('Ratnapura',    'SGP')
) AS d (district, zone_code)
JOIN zone z ON z.code = d.zone_code
ON CONFLICT (district) DO NOTHING;
