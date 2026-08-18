-- ============================================================================
-- V2__seed_categories.sql — reference data
-- ============================================================================
insert into categories (id, name, default_shelf_life_days, warning_threshold_days)
values
    (gen_random_uuid(), 'grocery',    30, 3),
    (gen_random_uuid(), 'medicine',  365, 7),
    (gen_random_uuid(), 'perishable',  7, 1)
on conflict (name) do nothing;