-- A hidden menu remains an enabled route with its permission intact; it is simply omitted from
-- navigation. This is required for detail and drill-down pages that have a visible parent entry.
ALTER TABLE system_menus
    ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;
