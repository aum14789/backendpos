-- Spec 0033 / ticket 05: contract phase of the warehouse role change.
--
-- `warehouse_role` (V2) is now the only place a warehouse's purpose is recorded, and nothing in
-- the codebase reads `is_central` any more. Dropping it is irreversible, which is the point: a
-- warehouse is central because its role says CENTRAL, not because a second column happens to agree.

ALTER TABLE warehouses DROP COLUMN IF EXISTS is_central;
