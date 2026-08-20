# 16 — Waste Management

## What "wasted" means in this system

Recording an item as wasted:

1. writes one `waste_log` row (quantity wasted + optional estimated cost
   lost + snapshot of name/unit + timestamp);
2. **deletes the item** from the pantry in the same transaction.

Partial waste is supported: the user may enter a quantity smaller than the
item's total (e.g. threw away half a pack). The remainder is not tracked —
the whole item row is removed either way; `quantity_wasted` records what was
thrown away.

## API

### `POST /api/items/{id}/waste`

```json
{"quantityWasted": 1.5, "estimatedCostLost": 3.25}
```

| Field | Behavior |
|---|---|
| `quantityWasted` | Optional; defaults to the item's full quantity; must be > 0 and ≤ item quantity (400 otherwise) |
| `estimatedCostLost` | Optional; stored as-is; formatted in the UI as INR |

Responses: 200 (item deleted + log written), 400 (bad quantity), 403
(foreign item), 404 (missing item).

### `GET /api/waste-log?limit=20`

Newest-first history for the caller; `limit` clamped 1–100. Used by the
dashboard (limit 6) and future history views.

## Snapshot design (why history survives)

The `waste_log` table stores `item_name` and `unit` at the moment of waste
(Flyway V3), and `item_id` is `ON DELETE SET NULL`. Without the snapshot the
entry would be blank after deletion — this is why dashboards and analytics
keep working after items are removed.

`WasteLog` is created with a **null item reference** in Java (`setItem(null)`
before `saveAndFlush`), because Hibernate forbids an insert that references
an entity scheduled for removal in the same transaction. The snapshot columns
are the single source of truth for display.

## Concurrency

`markWasted` uses `ItemRepository.findOwnedForUpdate(id, ownerId)`
(`SELECT … FOR UPDATE` scoped by owner). Concurrent waste requests for the
same item are serialized: only one transaction can create the WasteLog and
delete the item. Verified by `MarkWastedConcurrencyTest`.

## Business rules

- `quantity_wasted > 0` (DB CHECK + service validation).
- `quantity_wasted ≤ item.quantity` (service validation).
- `estimated_cost_lost ≥ 0` (DB CHECK).
- The item is deleted — a subsequent `GET/DELETE` on it returns 404.
  The production smoke test asserts exactly this behavior.

## UI

- `WasteModal` (from `ItemActions` or `ItemCard`): quantity defaults to the
  item's full quantity; cost is optional; warns that the item will be
  removed and added to analytics.
- Success toast: "Waste recorded and item removed".
- Dashboard "Recent waste" card (last 6 entries) and analytics aggregation
  consume `waste_log`.