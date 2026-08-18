-- ============================================================================
-- V1__init.sql — Smart Expiry & Pantry Waste Tracker schema
-- Runs against Supabase-hosted Postgres via Flyway on Spring Boot startup.
-- No RLS: authorization lives in the Spring service layer (ownership checks).
-- ============================================================================

create type user_role as enum ('USER', 'ADMIN');
create type notification_type as enum ('EXPIRING_SOON', 'EXPIRED');

-- ----------------------------------------------------------------------------
-- users — owned by Spring Security (BCrypt-hashed passwords), replacing
-- any external auth provider.
-- ----------------------------------------------------------------------------
create table users (
    id            uuid primary key,
    email         varchar(320) not null unique,
    password_hash varchar(255) not null,
    display_name  varchar(100),
    role          user_role not null default 'USER',
    created_at    timestamptz not null default now()
);

-- ----------------------------------------------------------------------------
-- categories — per-category "expiring soon" windows instead of one global
-- constant (medicines warn 7 days out, perishables 1 day out, groceries 3).
-- ----------------------------------------------------------------------------
create table categories (
    id                       uuid primary key,
    name                     varchar(50) not null unique,
    default_shelf_life_days  integer not null default 3,
    warning_threshold_days   integer not null default 3
);

-- ----------------------------------------------------------------------------
-- items — owner_id is the authorization key used by every service-layer
-- ownership check.
-- ----------------------------------------------------------------------------
create table items (
    id                uuid primary key,
    owner_id          uuid not null references users (id) on delete cascade,
    name              varchar(200) not null,
    barcode           varchar(32),
    category_id       uuid not null references categories (id),
    quantity          numeric(10, 2) not null default 1 check (quantity >= 0),
    unit              varchar(20) not null default 'unit',
    purchase_date     date,
    expiry_date       date,
    shelf_life_days   integer,
    notes             text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index items_owner_expiry_idx on items (owner_id, expiry_date);
create index items_barcode_idx on items (barcode);

-- ----------------------------------------------------------------------------
-- waste_log — one row per "threw it away" event; feeds the analytics charts.
-- item_id is kept nullable (set null) so history survives item deletion.
-- ----------------------------------------------------------------------------
create table waste_log (
    id                   uuid primary key,
    item_id              uuid references items (id) on delete set null,
    user_id              uuid not null references users (id) on delete cascade,
    quantity_wasted      numeric(10, 2) not null check (quantity_wasted > 0),
    estimated_cost_lost  numeric(10, 2) check (estimated_cost_lost >= 0),
    logged_at            timestamptz not null default now()
);

create index waste_log_user_idx on waste_log (user_id, logged_at desc);

-- ----------------------------------------------------------------------------
-- notifications — audit + de-duplication for the scheduled email job.
-- The unique index on (item_id, type, day) makes re-runs idempotent at the
-- database level, even if two job executions race.
-- ----------------------------------------------------------------------------
create table notifications (
    id        uuid primary key,
    item_id   uuid references items (id) on delete cascade,
    user_id   uuid not null references users (id) on delete cascade,
    type      notification_type not null,
    channel   varchar(20) not null default 'email',
    sent_at   timestamptz not null default now()
);

create unique index notifications_dedup_idx
    on notifications (item_id, type, date_trunc('day', sent_at, 'UTC'));
create index notifications_user_idx on notifications (user_id, sent_at desc);

-- ----------------------------------------------------------------------------
-- product_cache — barcode → Open Food Facts payload, cached server-side so
-- repeat scans never re-hit the public API.
-- ----------------------------------------------------------------------------
create table product_cache (
    barcode    varchar(32) primary key,
    payload    jsonb not null,
    fetched_at timestamptz not null default now()
);

-- ----------------------------------------------------------------------------
-- updated_at maintenance
-- ----------------------------------------------------------------------------
create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger items_set_updated_at
    before update on items
    for each row execute function set_updated_at();