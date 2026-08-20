# 17 — Analytics Design

## Backend: `AnalyticsService.monthlyWaste(userId, months)`

```
months = clamp(months, 1, 24)          # default 6 (controller)
for i = months-1 … 0:                  # oldest month first
    ym     = YearMonth.now().minusMonths(i)
    from   = ym.atDay(1)               # local midnight → Instant
    toExcl = ym.plusMonths(1).atDay(1)
    wastedItems = waste_log.count(user_id, logged_at ∈ [from, toExcl))
    costLost    = Σ estimated_cost_lost (non-null) in the same window
    totalItems  = items.count(owner_id, created_at ∈ [from, toExcl))
totals: totalCostLost = Σ costLost, totalWasted = Σ wastedItems
```

- Queries: `WasteLogRepository.findByUserIdAndLoggedAtBetween` and
  `ItemRepository.countByOwnerIdAndCreatedAtBetween` (both scoped by user).
- Time zone: month boundaries use the server's system zone.
- Response: `MonthlyWasteResponse{months: MonthlyPoint[], totalCostLost,
  totalWasted}`; `MonthlyPoint{month: "YYYY-MM", totalItems, wastedItems,
  costLost}`.

## Endpoint

`GET /api/analytics/monthly-waste?months=6` — authenticated,
`@AuthenticationPrincipal` supplies the user id; the caller can never query
another user's analytics.

## Frontend (`pages/Analytics.tsx`)

- Range selector: 3 / 6 / 12 months (server accepts 1–24).
- KPI cards: **Items wasted** and **Estimated cost lost** (formatted as INR
  via `lib/money.ts`, `Intl.NumberFormat('en-IN', {currency:'INR'})`).
- `WasteChart`: filled rose line chart of wasted items per month
  (theme-aware Chart.js options from `lib/chart.ts`).
- "By the numbers": monthly table (month, items wasted, cost lost),
  newest month first.

## Dashboard components

- `SummaryCards`: Safe / Expiring soon / Expired / Total items (clickable
  status filter when `onFilterClick` provided — the summary cards on the
  dashboard are display-only; ItemList provides its own status filter).
- `CategoryDonut`: doughnut of items per category (6-color palette).
- "Needs attention": worst 5 items by `daysUntilExpiry`.

## What the numbers mean

| Metric | Definition |
|---|---|
| `totalItems` | Items **added** in that calendar month (created_at) |
| `wastedItems` | Waste events logged in that calendar month |
| `costLost` | Sum of user-entered estimated costs in that month |
| `totalWasted` / `totalCostLost` | Totals across the requested window |

Limitation (as designed): cost lost is a user-entered estimate, not a
computed value; analytics only cover waste events (not consumption).