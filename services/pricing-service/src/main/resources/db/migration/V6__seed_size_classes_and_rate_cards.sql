-- pricing-service (database: pricing_db): reviewed size-class and rate-card reference data.
-- Fees are LKR. Every fee literal belongs here; none may appear in Java or frontend code.

INSERT INTO size_class (code, max_weight_kg, max_dimensions_cm, multiplier) VALUES
    ('S',   2.00, '30x20x15', 1.000),
    ('M',   5.00, '40x30x20', 1.150),
    ('L',  15.00, '60x40x30', 1.350),
    ('XL', 30.00, '80x60x40', 1.600)
ON CONFLICT (code) DO NOTHING;

-- One rate card per ordered zone pair: 9 zones gives 81 rows.
--
-- The distance band is the number of province borders a parcel must cross, which is the
-- shortest path between the two zones over the adjacency list below. Counting hops rather
-- than just "do these two provinces touch" matters: Western and Central do not share a
-- border, but Colombo to Kandy is one province apart and must not cost the same as
-- Colombo to Jaffna, which is three.
--
--   band 1  same zone
--   band 2  one border crossed
--   band 3  two borders crossed
--   band 4  three or more borders crossed
--
-- Adjacency is reference data, so it is declared here rather than derived in code.
WITH RECURSIVE border (a, b) AS (
    VALUES
        ('WP',  'NWP'), ('WP',  'SGP'), ('WP',  'SP'),
        ('CP',  'NWP'), ('CP',  'NCP'), ('CP',  'EP'), ('CP', 'UP'), ('CP', 'SGP'),
        ('SP',  'SGP'), ('SP',  'UP'),
        ('NP',  'NCP'), ('NP',  'EP'),
        ('EP',  'NCP'), ('EP',  'UP'),
        ('NWP', 'NCP'), ('NWP', 'SGP'),
        ('UP',  'SGP')
),
-- Borders are undirected; traverse them both ways.
edge (a, b) AS (
    SELECT a::text, b::text FROM border
    UNION
    SELECT b::text, a::text FROM border
),
-- Breadth-first walk from every zone, so `hops` is the shortest path length.
reachable (origin, destination, hops) AS (
    SELECT z.code::text, z.code::text, 0 FROM zone z
    UNION
    SELECT r.origin, e.b, r.hops + 1
    FROM reachable r
    JOIN edge e ON e.a = r.destination
    WHERE r.hops < 8
),
distance (origin, destination, hops) AS (
    SELECT origin, destination, min(hops) FROM reachable GROUP BY origin, destination
),
band_rate (band, base_fee, per_kg_fee) AS (
    VALUES
        (1, 250.00, 40.00),
        (2, 350.00, 60.00),
        (3, 475.00, 78.00),
        (4, 600.00, 95.00)
)
INSERT INTO rate_card (
    id, from_zone_id, to_zone_id, base_fee, per_kg_fee,
    express_multiplier, cod_fee_pct, effective_from, effective_to
)
SELECT gen_random_uuid(), origin.id, destination.id, r.base_fee, r.per_kg_fee,
       1.500, 0.0150, DATE '2026-01-01', NULL
FROM zone origin
CROSS JOIN zone destination
JOIN distance d ON d.origin = origin.code AND d.destination = destination.code
JOIN band_rate r ON r.band = least(d.hops + 1, 4)
ON CONFLICT (from_zone_id, to_zone_id, effective_from) DO NOTHING;
