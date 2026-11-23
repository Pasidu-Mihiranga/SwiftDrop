-- The V16 configuration becomes administrator-maintained (/admin/pricing/settings), and the fee
-- settings gain a same-day order cut-off.
--
-- The V16 seed values stay as they are: they are the starting configuration, and changing them is
-- now an admin action rather than a migration. Settings and distance bands are published as new
-- versions from a date (the old row is closed, never rewritten), so an older quote's pricing
-- version still points at the configuration it was priced from. Handling options and vehicles are
-- edited in place, as size classes are; a quote keeps its own copy of the fee it was given.

-- Local time (the quote zone, Asia/Colombo by default) from which same-day work is no longer taken
-- for the same day. NULL means same-day can be ordered at any time, which keeps today's behaviour
-- until an administrator publishes a cut-off.
ALTER TABLE pricing_setting ADD COLUMN same_day_cutoff TIME;

COMMENT ON TABLE pricing_setting IS
    'Fee-wide settings, versioned by effective_from. Seeded by V16; maintained on /admin/pricing/settings.';
COMMENT ON TABLE distance_band IS
    'Line-haul bands, published as a complete set from a date. Seeded by V16; maintained on /admin/pricing/settings.';
COMMENT ON TABLE special_handling IS
    'Handling options and their fees. Seeded by V16; maintained on /admin/pricing/settings.';
COMMENT ON TABLE same_day_vehicle IS
    'Same-day vehicles and their limits. Seeded by V16; maintained on /admin/pricing/settings.';
COMMENT ON COLUMN pricing_setting.same_day_cutoff IS
    'Local time from which same-day is refused for today (quote zone). NULL: no cut-off.';
