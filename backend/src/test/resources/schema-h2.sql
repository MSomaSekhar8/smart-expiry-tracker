-- Test-only H2 schema mirroring the entity model (production schema is
-- managed by Flyway against Postgres; these tables exist so the pessimistic
-- locking integration tests can run against an embedded database).

create table users (
    id                 uuid primary key,
    email              varchar(320) not null unique,
    password_hash      varchar(255) not null,
    display_name       varchar(100),
    role               varchar(20) not null default 'USER',
    created_at         timestamp not null default current_timestamp,
    refresh_generation bigint not null default 0
);

create table categories (
    id                      uuid primary key,
    name                    varchar(50) not null unique,
    default_shelf_life_days integer not null default 3,
    warning_threshold_days  integer not null default 3
);

create table items (
    id              uuid primary key,
    owner_id        uuid not null,
    name            varchar(200) not null,
    barcode         varchar(32),
    category_id     uuid not null,
    quantity        numeric(10, 2) not null default 1,
    unit            varchar(20) not null default 'unit',
    purchase_date   date,
    expiry_date     date,
    shelf_life_days integer,
    notes           clob,
    created_at      timestamp not null default current_timestamp,
    updated_at      timestamp not null default current_timestamp
);

create table waste_log (
    id                  uuid primary key,
    item_id             uuid,
    user_id             uuid not null,
    item_name           varchar(200),
    unit                varchar(20),
    quantity_wasted     numeric(10, 2) not null,
    estimated_cost_lost numeric(10, 2),
    logged_at           timestamp not null default current_timestamp
);