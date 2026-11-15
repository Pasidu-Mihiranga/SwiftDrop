-- Geography is fixed master data: nine provinces, 25 districts, and which province each
-- district belongs to. None of it is a pricing decision, so none of it is editable from the
-- admin screens any more (the write endpoints are gone) and this migration makes the database
-- refuse the change as well.
--
-- 1. Restore the canonical mapping. Earlier releases let an administrator move a district, so
--    a long-lived database may not match V5. This puts every district back in its province
--    (Ampara in particular, which has been seen in the Central zone) and is a no-op on a
--    database that already matches.
-- 2. Make sure the nine province zones are active. A deactivated province zone would make all
--    of its districts unquotable, and there is no longer any screen to switch it back on.
-- 3. Add triggers that reject writes to district_zone and to a zone's identity.
--
-- A future migration that genuinely has to change geography (a new district, say) runs
--     SET LOCAL swiftdrop.allow_geography_change = 'on';
-- first, inside its own transaction. Nothing in the application sets it.

UPDATE district_zone dz
   SET zone_id = z.id
  FROM (VALUES
      ('Colombo', 'WP'), ('Gampaha', 'WP'), ('Kalutara', 'WP'),
      ('Kandy', 'CP'), ('Matale', 'CP'), ('Nuwara Eliya', 'CP'),
      ('Galle', 'SP'), ('Matara', 'SP'), ('Hambantota', 'SP'),
      ('Jaffna', 'NP'), ('Kilinochchi', 'NP'), ('Mannar', 'NP'), ('Mullaitivu', 'NP'), ('Vavuniya', 'NP'),
      ('Ampara', 'EP'), ('Batticaloa', 'EP'), ('Trincomalee', 'EP'),
      ('Kurunegala', 'NWP'), ('Puttalam', 'NWP'),
      ('Anuradhapura', 'NCP'), ('Polonnaruwa', 'NCP'),
      ('Badulla', 'UP'), ('Monaragala', 'UP'),
      ('Kegalle', 'SGP'), ('Ratnapura', 'SGP')
  ) AS canonical (district, zone_code)
  JOIN zone z ON z.code = canonical.zone_code
 WHERE dz.district = canonical.district
   AND dz.zone_id IS DISTINCT FROM z.id;

UPDATE zone
   SET active = TRUE
 WHERE code IN ('WP', 'CP', 'SP', 'NP', 'EP', 'NWP', 'NCP', 'UP', 'SGP')
   AND active = FALSE;

CREATE FUNCTION reject_geography_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF coalesce(current_setting('swiftdrop.allow_geography_change', true), '') = 'on' THEN
        RETURN coalesce(NEW, OLD);
    END IF;
    RAISE EXCEPTION 'Geography is fixed master data: % on % is not allowed', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER trg_district_zone_fixed
    BEFORE INSERT OR UPDATE OR DELETE ON district_zone
    FOR EACH ROW EXECUTE FUNCTION reject_geography_change();

CREATE TRIGGER trg_zone_fixed_rows
    BEFORE INSERT OR DELETE ON zone
    FOR EACH ROW EXECUTE FUNCTION reject_geography_change();

-- Only the identity is guarded. Nothing updates a zone today, but an UPDATE that leaves code,
-- name and active alone (a no-op save) is not worth failing over.
CREATE TRIGGER trg_zone_fixed_identity
    BEFORE UPDATE OF code, name, active ON zone
    FOR EACH ROW
    WHEN (OLD.code IS DISTINCT FROM NEW.code
          OR OLD.name IS DISTINCT FROM NEW.name
          OR OLD.active IS DISTINCT FROM NEW.active)
    EXECUTE FUNCTION reject_geography_change();
