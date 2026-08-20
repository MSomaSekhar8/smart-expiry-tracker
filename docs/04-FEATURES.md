# 04 — Features

All features are verified against the actual source code.

## Authentication

- Email/password registration (password ≥ 8 chars, email lowercased, duplicate
  email rejected with 409).
- Login with BCrypt-verified passwords; generic "Invalid email or password"
  on failure (no user-enumeration hint).
- JWT access token (60 min, HS256, `typ=access`) returned in the JSON body
  and kept in browser memory only.
- Refresh token (14 days) in an HttpOnly cookie at `Path=/api/auth`, rotated
  on every refresh with generation-based revocation.
- Logout revokes every outstanding refresh token for the user and clears the
  cookie.
- Session restore on page reload (refresh cookie → new access token →
  `/auth/me`).
- Single-flight 401 handling: parallel 401s trigger exactly one refresh
  request; failed refresh signs the user out (`auth:unauthorized` event).
- Roles `USER` / `ADMIN`; admin-only digest trigger.

## Pantry management

- Add / edit / delete items (name, category, quantity, unit, purchase date,
  expiry date, shelf life, notes).
- Add via a modal (dashboard and item list) or a dedicated page
  (`/items/new`).
- Quantity stepper (1–999) with decimal support and client-side clamping.
- Unit suggestions per category (grocery → kg, medicine → pcs, perishable →
  kg) with a free choice from a fixed list.
- Shelf-life auto-suggestion: computed from purchase date → expiry date, or
  defaulted from the category's `defaultShelfLifeDays` when only the category
  is set.
- Search (300 ms debounced), category filter, status filter, sorting by
  expiry/name/date-added/category, ascending/descending.
- Responsive list: table on desktop, cards on mobile.
- Optimistic local list updates after create/update/delete/waste
  (`hooks/useItems.ts`).

## Expiry tracking

- Status computed on every read: `SAFE`, `EXPIRING`, `EXPIRED`.
- Per-category warning windows: `grocery` 3 days, `medicine` 7 days,
  `perishable` 1 day (seeded reference data).
- `daysUntilExpiry` in every item response; "Needs attention" list on the
  dashboard (worst first, top 5).
- Summary cards: Safe / Expiring soon / Expired / Total items.
- Status badges with color coding (emerald / amber / rose) and day labels
  ("Expires today", "Expires tomorrow", "In N days").

## Categories

- Read-only category reference data (`GET /api/categories`).
- Seeded by Flyway V2: `grocery` (30/365/7 shelf-life, 3/7/1 warnings).

## Waste management

- "Mark as wasted" from item actions: quantity wasted (defaults to full
  quantity) + estimated cost lost (optional).
- Validation: quantity must be > 0 and ≤ item quantity; item is deleted
  afterwards and a `WasteLog` row is written with a historical snapshot
  (item name + unit) so history survives deletion.
- Recent waste feed on the dashboard (last 6 entries) and full history
  endpoint (`GET /api/waste-log`, paginated limit 1–100).
- Waste recorded in the currency of the app UI: Indian Rupees
  (`lib/money.ts` — `Intl.NumberFormat('en-IN', { currency: 'INR' })`).

## Analytics

- Monthly waste API: per month (items wasted, items added, cost lost) for
  1–24 months, plus totals.
- Analytics page with a waste-trend line chart, total items wasted, total
  estimated cost lost, and a "by the numbers" monthly table.
- Time-range selector: 3 / 6 / 12 months.
- Category donut chart on the dashboard (items per category).

## Barcode

- Camera scanning with `html5-qrcode` (rear camera, 10 fps, viewfinder box).
- Confirmation logic: 3 consistent reads within a 7-observation window beat a
  runner-up (2× rule) before accepting — prevents flaky scans.
- Image upload decoding (≤ 10 MB) with friendly error messages.
- Manual entry with client-side check-digit validation for EAN-13, EAN-8,
  UPC-A (and generic 8–14 digit fallback).
- Server-side lookup on Open Food Facts with a Postgres cache; results
  auto-fill item name and category in the form.
- Friendly camera error messages (permission denied, no camera, in use,
  over-constrained).

## Notifications & email

- Daily digest job at 07:00 (`DIGEST_CRON`, default `0 0 7 * * *`).
- One HTML email per user containing only that user's items (two sections:
  "Expiring soon" / "Already expired").
- Idempotent per item per UTC day (unique index), safe against racing job
  runs; failed sends are retried on the next run.
- Dry-run mode when `RESEND_API_KEY` is not set (logs counts only — no item
  names, no addresses).
- Admin "Test digest now" button on the Settings page
  (`POST /api/admin/digest/test`).

## Platform & deployment

- Health endpoint for uptime checks.
- Render + Vercel + Supabase deployment (see `21-DEPLOYMENT-ARCHITECTURE.md`).
- SPA deep-link rewrites (`vercel.json`).
- Production smoke test script (15 checks, 15/15 PASS).