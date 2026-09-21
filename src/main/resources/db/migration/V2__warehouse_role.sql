-- Spec 0033 / ticket 02: a warehouse's purpose becomes data instead of a naming convention.
--
-- Expand phase of an expand-contract change: this adds `warehouse_role` and backfills it from the
-- rows that already exist, while `is_central` stays in place until ticket 05. Nothing reads the new
-- column yet, so this migration is safe to apply before the code that depends on it ships.

ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS warehouse_role VARCHAR(20);

-- Before this column existed, a warehouse's purpose could only be guessed from its name or code.
-- This is the one and only place that guess is allowed to run; ticket 05 removes it from runtime.
-- The order below is deliberate: specialized warehouses are recognized before the central kitchen,
-- and everything left over is an ordinary branch warehouse.
UPDATE warehouses
   SET warehouse_role = CASE
       WHEN lower(name) LIKE '%เวส%' OR lower(name) LIKE '%waste%'
         OR lower(code) LIKE '%waste%'                                     THEN 'WASTE'
       WHEN lower(name) LIKE '%ทำลาย%' OR lower(name) LIKE '%เดสทรอย%'
         OR lower(name) LIKE '%destroy%' OR lower(code) LIKE '%destroy%'   THEN 'DESTROY'
       WHEN is_central                                                      THEN 'CENTRAL'
       ELSE 'MAIN'
   END
 WHERE warehouse_role IS NULL;

-- A branch has exactly one operational main warehouse. When the existing rows do not already
-- satisfy that, stop and let a human decide which warehouse it is -- do not invent roles to make
-- the constraint below pass.
DO $$
DECLARE
    ambiguous TEXT;
BEGIN
    SELECT string_agg(branch_id || ' has ' || main_count || ' active main warehouses', '; ')
      INTO ambiguous
      FROM (
          SELECT branch_id, COUNT(*) AS main_count
            FROM warehouses
           WHERE warehouse_role = 'MAIN' AND is_active
           GROUP BY branch_id
          HAVING COUNT(*) > 1
      ) conflicting_branches;

    IF ambiguous IS NOT NULL THEN
        RAISE EXCEPTION 'Warehouse role backfill is ambiguous: %. Assign warehouse_role so each branch has one, then re-run.', ambiguous;
    END IF;
END $$;

ALTER TABLE warehouses ALTER COLUMN warehouse_role SET NOT NULL;

-- An inactive warehouse is retired: it keeps its history but stops being the branch's main
-- warehouse, which is what lets an operator replace one without deleting the old row.
CREATE UNIQUE INDEX IF NOT EXISTS uk_warehouses_branch_main_role
    ON warehouses (branch_id)
 WHERE warehouse_role = 'MAIN' AND is_active;
