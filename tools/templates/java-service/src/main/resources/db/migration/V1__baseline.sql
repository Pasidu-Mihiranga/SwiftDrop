-- __service__ schema baseline (database: __db__).
-- Add tables in new files: V2__create_<table>.sql, V3__... Never edit a migration that has been merged.
{{#kafka}}-- outbox_event and processed_event come from libs/java/swiftdrop-outbox (V0_1, V0_2).
{{/kafka}}SELECT 1;
