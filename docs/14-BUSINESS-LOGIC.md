# 14 — Business Logic

## Expiry status (`ItemStatusService`)

```
status(expiryDate, warningThresholdDays, today = LocalDate.now()):
  expiryDate == null            → SAFE
  expiryDate <  today           → EXPIRED
  expiryDate <= today + threshold → EXPIRING
  otherwise                     → SAFE
```

- The threshold is a **category property**, so medicines warn 7 days out,
  perishables 1 day, groceries 3.
- Status is computed on every read (`ItemService.toResponse`) and in the
  digest planner — never stored.

## Item listing (`ItemService.list`)

1. Base query: `findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId)` —
   index-backed (`items_owner_expiry_idx`).
2. In-memory filters:
   - `search` — case-insensitive name substring;
   - `category` — exact UUID match;
   - `status` — exact enum; unknown value → 400 `Invalid status filter`.
3. Sorting: `expiry` (nulls last, default), `name` (case-insensitive),
   `created`, `category`; `dir=desc` reverses.

## Ownership rule

Every read/update/delete of an item goes through
`OwnershipGuard.requireOwner(ownerId, currentUserId, "item")` → 403 for
foreign items. `markWasted` and refresh rotation scope the locking query by
owner instead (safer, one round-trip).

## Waste recording (`ItemService.markWasted`)

1. `findOwnedForUpdate(itemId, userId)` — `SELECT … FOR UPDATE WHERE id=? AND
   owner.id=?`. If the item exists but belongs to someone else → 403; if it
   does not exist → 404.
2. `quantityWasted` defaults to the full item quantity; must be > 0 and ≤
   item quantity, else 400.
3. Creates `WasteLog(user, item=null, quantityWasted, estimatedCostLost)` —
   the item reference is deliberately null because the item is deleted in
   the same transaction (avoids a Hibernate
   `TransientObjectException`; snapshot columns `item_name`/`unit` are the
   source of truth).
4. Deletes the item.

## Digest planning (`ExpiryDigestService.run`)

1. Load all items with owner + category (eager `EntityGraph`), group by
   owner.
2. Per user: skip items without an expiry date, skip `SAFE`, skip items
   already notified today (UTC-day boundary).
3. Send ONE email per user (outside any transaction).
4. Record a `Notification` per item **only after** the send succeeds;
   failed sends are never recorded → retried on the next run; one user's
   failure never aborts the batch.

## Barcode lookup (`BarcodeService.lookup`)

1. Validate `^\d{8,14}$` → 400 otherwise.
2. Cache hit (`product_cache` by barcode) → return `cached: true`.
3. Miss → Open Food Facts via WebClient (10 s timeout); errors → 400
   `Barcode service unavailable right now`; `status != 1` → 400 `Product
   not found for this barcode`.
4. Cache write in `REQUIRES_NEW`; a write failure never fails the lookup
   (product is returned with `cached: false`).
5. Map `product_name`, `brands`, first `categories_tags` (strip `en:` prefix,
   dashes → spaces).

## Validation rules

| Rule | Location |
|---|---|
| Email valid + required; password ≥ 8 chars; display name ≤ 100 | `AuthDtos.RegisterRequest` |
| Name required (≤ 200); category required; barcode ≤ 32; quantity ≥ 0; unit ≤ 20; shelf life ≥ 0; notes ≤ 5000 | `ItemDtos.UpsertRequest` |
| Quantity ≥ 0 (DB check) | `items.quantity` CHECK |
| Wasted quantity > 0 and ≤ item quantity | `ItemService.markWasted` |
| Barcode 8–14 digits | `BarcodeService` |
| Waste-log cost ≥ 0 (DB check) | `waste_log.estimated_cost_lost` CHECK |

## Analytics aggregation (`AnalyticsService.monthlyWaste`)

For each of the last N months (N clamped 1–24, oldest first):

- `wastedItems` = count of `waste_log` rows in the month (`logged_at` between
  month start and next-month start);
- `costLost` = sum of non-null `estimated_cost_lost` in the month;
- `totalItems` = count of `items` created in the month.

Totals: sum of cost lost and wasted items across the window.

## Concurrency rules

- `markWasted` is serialized per item by a pessimistic row lock.
- Refresh rotation is serialized per user by a pessimistic row lock.
- Digest notifications are idempotent via the unique index
  `(item_id, type, utc-day)`.
- Product-cache writes are isolated (`REQUIRES_NEW`) and failure-tolerant.