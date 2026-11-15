-- Real dimensions for each size class.
--
-- max_dimensions_cm has always been a display string such as '40x30x20'. Nothing parsed it,
-- so the only limit the service could actually enforce was weight: a parcel of any shape was
-- accepted into class S as long as it was light enough. These columns make the geometry a
-- first-class limit, which is what lets a size class be derived from a parcel rather than
-- claimed by the customer.
--
-- The string column stays, because it is a required field of the published SizeClassRate
-- schema. It becomes a label rendered from these three numbers rather than a separate truth.

ALTER TABLE size_class
    ADD COLUMN max_length_cm NUMERIC(6, 1),
    ADD COLUMN max_width_cm  NUMERIC(6, 1),
    ADD COLUMN max_height_cm NUMERIC(6, 1);

-- Backfill from the seeded 'LxWxH' values.
UPDATE size_class SET
    max_length_cm = split_part(max_dimensions_cm, 'x', 1)::numeric,
    max_width_cm  = split_part(max_dimensions_cm, 'x', 2)::numeric,
    max_height_cm = split_part(max_dimensions_cm, 'x', 3)::numeric
WHERE max_dimensions_cm ~ '^[0-9]+(\.[0-9]+)?x[0-9]+(\.[0-9]+)?x[0-9]+(\.[0-9]+)?$';

-- A row an administrator had edited into genuinely free text has nothing to parse. Fall back
-- to the reviewed geometry so the columns can be NOT NULL in this one migration.
UPDATE size_class SET max_length_cm = d.l, max_width_cm = d.w, max_height_cm = d.h
FROM (VALUES ('S', 30, 20, 15), ('M', 40, 30, 20), ('L', 60, 40, 30), ('XL', 80, 60, 40))
    AS d (code, l, w, h)
WHERE size_class.code = d.code AND size_class.max_length_cm IS NULL;

ALTER TABLE size_class
    ALTER COLUMN max_length_cm SET NOT NULL,
    ALTER COLUMN max_width_cm  SET NOT NULL,
    ALTER COLUMN max_height_cm SET NOT NULL,
    ADD CONSTRAINT ck_size_class_dimensions_positive
        CHECK (max_length_cm > 0 AND max_width_cm > 0 AND max_height_cm > 0);
