# 11 — ER Diagram

```mermaid
erDiagram
    users ||--o{ items : "owns"
    users ||--o{ waste_log : "wastes"
    users ||--o{ notifications : "receives"
    categories ||--o{ items : "classifies"
    items ||--o{ waste_log : "snapshotted"
    items ||--o{ notifications : "triggers"

    users {
        uuid id PK
        varchar email UK "NOT NULL"
        varchar password_hash "NOT NULL, BCrypt"
        varchar display_name "nullable"
        user_role role "default USER"
        timestamptz created_at "default now()"
        bigint refresh_generation "default 0"
    }

    categories {
        uuid id PK
        varchar name UK "NOT NULL"
        integer default_shelf_life_days "default 3"
        integer warning_threshold_days "default 3"
    }

    items {
        uuid id PK
        uuid owner_id FK "NOT NULL, on delete cascade"
        varchar name "NOT NULL"
        varchar barcode "nullable"
        uuid category_id FK "NOT NULL"
        numeric quantity "default 1, >= 0"
        varchar unit "default unit"
        date purchase_date "nullable"
        date expiry_date "nullable"
        integer shelf_life_days "nullable"
        text notes "nullable"
        timestamptz created_at "default now()"
        timestamptz updated_at "trigger-maintained"
    }

    waste_log {
        uuid id PK
        uuid item_id FK "on delete set null"
        uuid user_id FK "NOT NULL, on delete cascade"
        varchar item_name "snapshot"
        varchar unit "snapshot"
        numeric quantity_wasted "> 0"
        numeric estimated_cost_lost ">= 0"
        timestamptz logged_at "default now()"
    }

    notifications {
        uuid id PK
        uuid item_id FK "on delete cascade"
        uuid user_id FK "NOT NULL, on delete cascade"
        notification_type type "EXPIRING_SOON | EXPIRED"
        varchar channel "default email"
        timestamptz sent_at "default now()"
    }

    product_cache {
        varchar barcode PK
        jsonb payload "Open Food Facts payload"
        timestamptz fetched_at "default now()"
    }
```

## Relationship semantics

| Relationship | Cardinality | Meaning |
|---|---|---|
| users → items | 1 : N | A user owns many items; deleting the user cascades to their items |
| categories → items | 1 : N | A category classifies many items; category deletion is NOT cascaded (FK only, categories are reference data) |
| items → waste_log | 1 : N (nullable) | Waste entries may reference the item; `ON DELETE SET NULL` keeps history when the item is deleted |
| users → waste_log | 1 : N | Waste log entries belong to the user; cascade on user delete |
| items → notifications | 1 : N | One item may be notified once per day per type; cascade on item delete |
| users → notifications | 1 : N | Notification audit per user; cascade on user delete |
| product_cache | standalone | Key-value cache (barcode → JSON payload); no FK relationships |

## Indexes (from V1)

| Index | On | Purpose |
|---|---|---|
| `items_owner_expiry_idx` | items(owner_id, expiry_date) | Fast per-user listing ordered by expiry |
| `items_barcode_idx` | items(barcode) | Item lookup by barcode |
| `waste_log_user_idx` | waste_log(user_id, logged_at DESC) | Recent-waste feed per user |
| `notifications_dedup_idx` (UNIQUE) | notifications(item_id, type, date_trunc('day', sent_at, 'UTC')) | Digest idempotency |
| `notifications_user_idx` | notifications(user_id, sent_at DESC) | Notification audit per user |

## Constraints summary

- Check constraints: `items.quantity >= 0`, `waste_log.quantity_wasted > 0`,
  `waste_log.estimated_cost_lost >= 0`.
- Unique constraints: `users.email`, `categories.name`, `product_cache.barcode`
  (PK), the dedup unique index.
- Foreign keys: `items.owner_id → users (CASCADE)`,
  `items.category_id → categories`,
  `waste_log.item_id → items (SET NULL)`,
  `waste_log.user_id → users (CASCADE)`,
  `notifications.item_id → items (CASCADE)`,
  `notifications.user_id → users (CASCADE)`.