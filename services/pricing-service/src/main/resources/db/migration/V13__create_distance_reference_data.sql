-- Reference data for measuring a lane rather than banding it.
--
-- Two things live here. The province adjacency list is lifted verbatim out of V6, where it
-- existed only inside a recursive CTE and so could not be read, explained or re-derived at
-- runtime. District centroids are new: nothing in the system had a coordinate before, so a
-- same-day job had nothing to measure between.
--
-- Centroids are approximate district centres, good to a kilometre or so. They are the fallback
-- when a caller gives no precise pickup and drop-off point; a real same-day booking should send
-- coordinates, because the centre of Colombo district is not where the parcel is.

CREATE TABLE zone_adjacency (
    from_zone_id UUID NOT NULL REFERENCES zone (id),
    to_zone_id UUID NOT NULL REFERENCES zone (id),
    PRIMARY KEY (from_zone_id, to_zone_id),
    CONSTRAINT ck_zone_adjacency_not_self CHECK (from_zone_id <> to_zone_id)
);

-- Stored both ways round, so a walk over it needs no union trick.
INSERT INTO zone_adjacency (from_zone_id, to_zone_id)
SELECT a.id, b.id
FROM (VALUES
    ('WP', 'NWP'), ('WP', 'SGP'), ('WP', 'SP'),
    ('CP', 'NWP'), ('CP', 'NCP'), ('CP', 'EP'), ('CP', 'UP'), ('CP', 'SGP'),
    ('SP', 'SGP'), ('SP', 'UP'),
    ('NP', 'NCP'), ('NP', 'EP'),
    ('EP', 'NCP'), ('EP', 'UP'),
    ('NWP', 'NCP'), ('NWP', 'SGP'),
    ('UP', 'SGP')
) AS e (a_code, b_code)
JOIN zone a ON a.code = e.a_code
JOIN zone b ON b.code = e.b_code
UNION
SELECT b.id, a.id
FROM (VALUES
    ('WP', 'NWP'), ('WP', 'SGP'), ('WP', 'SP'),
    ('CP', 'NWP'), ('CP', 'NCP'), ('CP', 'EP'), ('CP', 'UP'), ('CP', 'SGP'),
    ('SP', 'SGP'), ('SP', 'UP'),
    ('NP', 'NCP'), ('NP', 'EP'),
    ('EP', 'NCP'), ('EP', 'UP'),
    ('NWP', 'NCP'), ('NWP', 'SGP'),
    ('UP', 'SGP')
) AS e (a_code, b_code)
JOIN zone a ON a.code = e.a_code
JOIN zone b ON b.code = e.b_code
ON CONFLICT DO NOTHING;

CREATE TABLE district_centroid (
    district VARCHAR(100) PRIMARY KEY REFERENCES district_zone (district),
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    CONSTRAINT ck_district_centroid_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_district_centroid_longitude CHECK (longitude BETWEEN -180 AND 180)
);

INSERT INTO district_centroid (district, latitude, longitude) VALUES
    ('Colombo',       6.927079,  79.861244),
    ('Gampaha',       7.091201,  79.994026),
    ('Kalutara',      6.585278,  79.960556),
    ('Kandy',         7.290572,  80.633728),
    ('Matale',        7.466800,  80.623200),
    ('Nuwara Eliya',  6.970700,  80.782600),
    ('Galle',         6.053519,  80.220978),
    ('Matara',        5.949100,  80.535400),
    ('Hambantota',    6.124200,  81.101500),
    ('Jaffna',        9.661500,  80.025500),
    ('Kilinochchi',   9.383300,  80.400000),
    ('Mannar',        8.977700,  79.911200),
    ('Mullaitivu',    9.267500,  80.814200),
    ('Vavuniya',      8.751100,  80.497100),
    ('Ampara',        7.291800,  81.672300),
    ('Batticaloa',    7.717100,  81.700100),
    ('Trincomalee',   8.587400,  81.215200),
    ('Kurunegala',    7.486100,  80.362500),
    ('Puttalam',      8.036200,  79.828300),
    ('Anuradhapura',  8.311400,  80.403700),
    ('Polonnaruwa',   7.939600,  81.000800),
    ('Badulla',       6.989300,  81.055000),
    ('Monaragala',    6.872400,  81.350700),
    ('Kegalle',       7.251300,  80.346400),
    ('Ratnapura',     6.705400,  80.384700)
ON CONFLICT (district) DO NOTHING;
