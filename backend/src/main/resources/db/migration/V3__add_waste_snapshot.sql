-- ============================================================================
-- V3__add_waste_snapshot.sql — historical snapshot of the wasted item
-- ----------------------------------------------------------------------------
-- waste_log.item_id is ON DELETE SET NULL, and mark-wasted deletes the item
-- in the same transaction. Without a snapshot the item name and unit would
-- be lost forever. These columns retain the values as they were at the time
-- the waste was logged. Nullable so pre-existing rows stay valid.
-- ============================================================================

alter table waste_log add column item_name varchar(200);
alter table waste_log add column unit varchar(20);