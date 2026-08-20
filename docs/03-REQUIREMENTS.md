# 03 — Requirements

Every requirement below is verified against the actual source code.

## Functional requirements

| ID | Requirement | Description | Status |
|---|---|---|---|
| FR-01 | Register account | Email, password (≥ 8 chars), optional display name; duplicate email → 409 | Implemented (`AuthService.register`, `AuthDtos.RegisterRequest`) |
| FR-02 | Login | Email/password → access token in body + refresh cookie | Implemented (`AuthService.login`) |
| FR-03 | Session restore | Page reload trades the refresh cookie for a new access token, then loads `/auth/me` | Implemented (`AuthContext`, `lib/apiClient.ts`) |
| FR-04 | Logout | Clears refresh cookie and revokes all outstanding refresh tokens (generation bump) | Implemented (`AuthService.revokeRefreshToken`) |
| FR-05 | List categories | `GET /api/categories` → id, name, defaultShelfLifeDays, warningThresholdDays | Implemented (`CategoryController`) |
| FR-06 | Create item | `POST /api/items`; category must exist (404); name required (400) | Implemented (`ItemService.create`) |
| FR-07 | Read item | `GET /api/items/{id}`; foreign item → 403, missing → 404 | Implemented (`ItemService.get` + `OwnershipGuard`) |
| FR-08 | Update item | `PUT /api/items/{id}` updates name, category, quantity, unit, dates, shelf life, notes | Implemented (`ItemService.update`) |
| FR-09 | Delete item | `DELETE /api/items/{id}`; waste history survives (snapshot) | Implemented (`ItemService.delete`) |
| FR-10 | List with filters | `search`, `category`, `status`, `sort` (expiry/name/created/category), `dir` | Implemented (`ItemService.list`) |
| FR-11 | Expiry status | SAFE / EXPIRING / EXPIRED per category warning threshold | Implemented (`ItemStatusService`) |
| FR-12 | Mark wasted | `POST /api/items/{id}/waste`; 0 < qty ≤ item qty; item deleted afterward | Implemented (`ItemService.markWasted`) |
| FR-13 | Waste history | `GET /api/waste-log?limit=` (clamped 1–100, default 20), newest first | Implemented (`WasteLogController`) |
| FR-14 | Monthly analytics | `GET /api/analytics/monthly-waste?months=` (clamped 1–24, default 6) | Implemented (`AnalyticsService`) |
| FR-15 | Barcode lookup | `GET /api/barcode/{code}` (8–14 digits) → name/brand/category, cached | Implemented (`BarcodeService`) |
| FR-16 | Barcode client validation | EAN-13 / EAN-8 / UPC-A check-digit validation before submit | Implemented (`lib/barcodeValidation.ts`) |
| FR-17 | Daily digest email | One email per user with only their own expiring/expired items | Implemented (`ExpiryDigestService`) |
| FR-18 | Digest idempotency | Unique index `(item_id, type, utc-day)` prevents duplicates | Implemented (`notifications_dedup_idx`, `NotificationRecorder`) |
| FR-19 | Health endpoint | `GET /api/health` → `{"status":"UP"}`, unauthenticated | Implemented (`HealthController`) |
| FR-20 | Admin digest trigger | `POST /api/admin/digest/test` (ROLE_ADMIN only) | Implemented (`DigestController`) |
| FR-21 | Auth rate limiting | Register 3/min, login 5/min, refresh 10/min per IP → 429 | Implemented (`AuthRateLimiter`) |
| FR-22 | Error contract | All errors → `{"message":"..."}` with proper status | Implemented (`GlobalExceptionHandler`) |
| FR-23 | Theme toggle | Light/dark theme, persisted (`pantry-theme`), respects system preference | Implemented (`ThemeContext`, `index.html`) |

## Non-functional requirements

| Category | Requirement | Design / Implementation | Status |
|---|---|---|---|
| Performance | Item listing for one user is index-backed | `items_owner_expiry_idx (owner_id, expiry_date)` | Implemented |
| Performance | Repeat barcode scans never hit the public API | `product_cache` (Postgres, `jsonb`) | Implemented |
| Security | Passwords never stored in plain text | BCrypt (`PasswordEncoder`) | Implemented |
| Security | Access token not persisted in browser storage | In-memory `tokenStore` in `lib/apiClient.ts` | Implemented |
| Security | Refresh token not readable by JavaScript | HttpOnly cookie, `Path=/api/auth` | Implemented |
| Security | Refresh token replay-safe | Rotation + generation counter + pessimistic lock (`findByIdForUpdate`) | Implemented |
| Security | No cross-user data access | `OwnershipGuard` in every service method; no RLS needed | Implemented |
| Security | Auth endpoints rate-limited | `AuthRateLimiter` (register 3, login 5, refresh 10 per minute per IP) | Implemented |
| Reliability | Failed email sends are retried, never marked sent | Notification recorded only after successful send | Implemented |
| Reliability | Racing digest runs cannot double-notify | Unique index + `REQUIRES_NEW` recorder | Implemented |
| Reliability | Cache write failures never break barcode lookups | `ProductCacheWriter` swallows and logs failures | Implemented |
| Maintainability | Schema changes versioned | Flyway V1–V4, `baseline-on-migrate: true` | Implemented |
| Maintainability | Feature-oriented backend layout | `auth`, `item`, `category`, `wastelog`, `analytics`, `barcode`, `notification`, `email`, `common`, `config` | Implemented |
| Testability | Core logic covered by automated tests | 146 tests (see `23-TESTING-VERIFICATION.md`) | Implemented |
| Operability | Production verifiable quickly | `production-smoke-test.ps1` (15 checks) | Implemented |
| Portability | Backend runs anywhere Docker exists | Multi-stage `backend/Dockerfile`, non-root user | Implemented |
| Observability | Uptime check without auth | `GET /api/health` | Implemented |
| Compliance | No secrets in the repository | `.env.example` placeholders; `.env*` git-ignored | Implemented |
| UX | Deep links work on the deployed SPA | `vercel.json` rewrite to `/index.html` | Implemented |
| Scalability | Horizontal scaling of the API | Stateless JWT auth; digest job assumes a single instance | Design goal |
| Load | Sustained load and latency targets | No load testing performed | Not verified from the current source. |
| Browser support | Modern evergreen browsers | Vite ES-module build | Not verified from the current source. |
| Accessibility | Keyboard-accessible UI | Radix primitives used | Not verified from the current source. |
| Disaster recovery | Backups / PITR | Supabase platform defaults | Not verified from the current source. |