# 08 — Frontend Architecture

## Directory layout

```
src/
├── main.tsx                 # Entry: StrictMode + BrowserRouter + ThemeProvider + AuthProvider
├── App.tsx                  # AppShell: loading gate, auth redirects, routes
├── index.css                # Tailwind entry + theme tokens
├── pages/                   # Dashboard, ItemList, ItemForm, Analytics, Login, Register, Settings
├── components/              # Feature components (ItemCard, WasteModal, BarcodeScannerInput, ...)
│   └── ui/                  # shadcn-style primitives (button, card, dialog, select, ...)
├── context/                 # AuthContext, ThemeContext
├── hooks/                   # useItems (items + categories)
└── lib/                     # apiClient, types, status, dates, money, chart, barcodeValidation,
                             # useQuery, utils
```

## Routing and route guards (`src/App.tsx`)

`AppShell` renders:

- a full-page skeleton while `AuthContext` restores the session;
- a redirect to `/login` when unauthenticated on a protected page;
- a redirect to `/dashboard` when authenticated on `/login` or `/register`;
- `Navbar` (hidden on auth pages) + the routes:

| Path | Page | Notes |
|---|---|---|
| `/login` | Login | Auth page |
| `/register` | Register | Auth page |
| `/dashboard` | Dashboard | Summary cards, needs attention, recent waste, category donut, recently added |
| `/items` | ItemList | Search/filter/sort, table (md+) / cards (mobile), inline edit dialog |
| `/items/new` | ItemForm | Create |
| `/items/:id/edit` | ItemForm | Edit (loads the item first) |
| `/analytics` | Analytics | Waste trend, totals, monthly table |
| `/settings` | Settings | Account info, logout, admin digest test |
| `*` | → `/dashboard` | Fallback |

## Authentication state (`context/AuthContext.tsx`)

- On mount: if no in-memory access token, call `refreshSession()` (trades the
  HttpOnly refresh cookie for a new access token), then `GET /auth/me`.
- `login` / `register` post credentials and store the returned
  `TokenResponse` (access token + user).
- `logout` calls `POST /auth/logout` (backend revokes generation + clears the
  cookie), clears the token regardless of the response.
- Listens for the `auth:unauthorized` window event (dispatched by
  `apiClient` when a refresh fails) and drops the user.

## API client (`lib/apiClient.ts`)

- axios instance with `baseURL = VITE_API_BASE_URL` and
  `withCredentials: true` (sends the refresh cookie; accepts `Set-Cookie`).
- Access token kept only in the module-level `tokenStore` (memory) — never
  localStorage/sessionStorage.
- Request interceptor adds `Authorization: Bearer <token>`.
- Response interceptor: on 401 of a non-auth request, performs a
  **single-flight** refresh (`refreshing` promise is shared across parallel
  401s), retries the original request once with the new token; on refresh
  failure, clears the token and dispatches `auth:unauthorized`.

## Data fetching

- `lib/useQuery.ts` — minimal hook: `{ data, setData, loading, error, refetch }`,
  cancellation-safe via a `mounted` flag.
- `hooks/useItems.ts` — `useItems(filters)` (GET `/items` with params) and
  `useCategories()` (GET `/categories`); mutations `createItem`,
  `updateItem`, `deleteItem`, `markWasted` update the local list
  optimistically after the server call succeeds.

## Forms and validation

- `ItemFormFields` (shared by `AddItemModal`, `ItemForm` page and the inline
  edit dialog):
  - name required; category required;
  - quantity stepper clamped to 1–999, decimal input regex
    `^\d*([.]\d{0,2})?$`, clamp on blur;
  - unit suggested per category (grocery→kg, medicine→pcs, perishable→kg)
    until the user touches it;
  - expiry auto-suggested from `defaultShelfLifeDays` when category is picked
    and no dates are set; shelf life auto-computed from purchase→expiry;
  - barcode auto-fills name and category from the lookup result.
- `Register` enforces password ≥ 8 characters client-side.
- `WasteModal` validates quantity > 0 and optional non-NaN cost.

## Charts (`lib/chart.ts`, `components/`)

- Chart.js registered once (`registerCharts`) with line + doughnut elements.
- `WasteChart` — waste-trend line (items wasted per month, filled,
  rose-colored) with theme-aware options.
- `CategoryDonut` — items per category, 6-color palette, bottom legend.
- `monthLabel` maps `YYYY-MM` to `Jan`…`Dec`.

## Barcode implementation (frontend)

`components/BarcodeScannerInput.tsx`:

- **Camera**: `html5-qrcode` started lazily (`facingMode: environment`,
  10 fps, viewfinder box ≤ 260×140 px). Acceptance requires 3 consistent
  reads within the last 7 observations and a 2× margin over the runner-up,
  with a 3 s confirmation timeout.
- **Image upload**: `scanFileV2` on a ≤ 10 MB image, validation by format.
- **Manual entry**: numeric input, check-digit validation
  (`lib/barcodeValidation.ts`: EAN-13, EAN-8, UPC-A; other digit strings
  allowed as `OTHER`), Enter or "Look up" button.
- **Lookup**: `GET /api/barcode/{code}`; result auto-fills the form and shows
  a "Found: …" toast; `cached` flag shown when served from the server cache.
- Friendly, localized error mapping for camera permission/availability.

## Notifications (UI)

- `sonner` toasts (`Toaster` mounted in `AppShell`) for success/error of
  create, update, delete, waste, digest test, barcode results.
- `auth:unauthorized` event drives the forced sign-out.

## Error handling (UI)

- `toErrorMessage` (`lib/types.ts`) extracts `{message}` from API errors with
  a generic fallback.
- Pages render error states (`EmptyState` with retry on ItemList, inline
  destructive text on forms, error cards on Analytics).

## Theming

- `ThemeContext` toggles the `dark` class on `<html>` and persists
  `pantry-theme` in localStorage; `index.html` applies the saved/system
  preference before the app loads (no flash).
- Fonts: Fraunces (display) + Inter (body) via Google Fonts.
- Brand: "Pantry Tracker" with a sprout mark (`AuthShell.Brand`).

## Build (`vite.config.ts`)

- Plugins: `react()`, `tailwindcss()`; alias `@` → `src`.
- Manual chunks: `charts`, `scanner`, `radix`.
- Throws at build time when `VITE_API_BASE_URL` is unset.
- `npm run build` = `tsc -b && vite build` (verified green).