-- Apply to the existing RDS database before deploying the paired-packaging API.
-- Existing count totals remain unchanged; component counts start unknown (NULL).
BEGIN;

ALTER TABLE inventory_count_template_lines
    ADD COLUMN IF NOT EXISTS paired_packaging INTEGER NOT NULL DEFAULT 0;

ALTER TABLE inventory_count_lines
    ADD COLUMN IF NOT EXISTS bottom_sleeves DOUBLE PRECISION;

ALTER TABLE inventory_count_lines
    ADD COLUMN IF NOT EXISTS lid_sleeves DOUBLE PRECISION;

UPDATE schema_version SET version = GREATEST(version, 24);

COMMIT;
