# 07 — System Design

## 1. Layered backend design

```
HTTP request
   │
   ▼
SecurityConfig (stateless) ──► JwtAuthFilter (Bearer access token)
   │
   ▼
Controller layer            @RestController, record DTOs, @AuthenticationPrincipal
   │
   ▼
Service layer               @Service, @Transactional, OwnershipGuard, business rules
   │
   ▼
Repository layer            Spring Data JPA (incl. pessimistic locks)
   │
   ▼
Flyway schema  ◄──────────►  PostgreSQL (Supabase)
```

- **Controllers are thin.** They map HTTP ↔ DTOs and delegate to services.
- **Services hold the business rules and transactions.** Read paths use
  `@Transactional(readOnly = true)`; writes use `@Transactional` and explicit
  row locks where concurrency matters.
- **Repositories are interfaces.** Derived queries plus a few `@Query`
  methods (`findOwnedForUpdate`, `findAllWithOwnerAndCategory`,
  `existsForItemToday`, `findByUserIdAndLoggedAtBetween`, …).
- **DTOs are Java records.** Notably `AuthDtos.AuthTokens` carries the refresh
  token internally; only the controller turns it into a cookie, so a refresh
  token is never serialized to JSON.

## 2. Frontend design

- **Contexts**: `AuthContext` (user, loading, login/register/logout, session
  restore, `auth:unauthorized` listener) and `ThemeContext` (light/dark,
  persisted under `pantry-theme`).
- **Data layer**: `lib/apiClient.ts` (axios instance, in-memory token store,
  request interceptor, single-flight 401 refresh) + `lib/useQuery.ts` (tiny
  async-data hook with `data/setData/loading/error/refetch`) +
  `hooks/useItems.ts` (items + categories hooks with local list mutations).
- **Pages** consume hooks and compose reusable components
  (`components/` and `components/ui/`).
- **Forms** are controlled React forms with client-side validation in
  `ItemFormFields` (name required, quantity 1–999, category required) and
  `Register` (password ≥ 8).

## 3. Concurrency design (backend)

| Scenario | Mechanism | Code |
|---|---|---|
| Two racing `markWasted` calls for the same item | `SELECT … FOR UPDATE` scoped to `(id, owner.id)`; only one transaction can log + delete | `ItemRepository.findOwnedForUpdate` |
| Two racing refresh rotations | Row lock on the user; second caller sees the bumped generation and is rejected | `UserRepository.findByIdForUpdate` |
| Two racing digest runs | Unique index `(item_id, type, utc-day)`; second insert throws, swallowed | `notifications_dedup_idx` + `NotificationRecorder` |
| Cache write colliding with lookup | Write in `REQUIRES_NEW`; failures logged and swallowed | `ProductCacheWriter` |
| Parallel 401s on the frontend | Single-flight refresh promise (one network call, then retries) | `lib/apiClient.ts` |

## 4. Configuration design

- Every runtime knob comes from an environment variable with a safe default,
  or fails fast:
  - `DB_URL`, `DB_PASSWORD`, `JWT_SECRET` — required, no defaults; the app
    refuses to start without them (constructor check in `JwtService`).
  - `JWT_ACCESS_TTL_MINUTES` (60), `JWT_REFRESH_TTL_DAYS` (14),
    `CORS_ALLOWED_ORIGINS` (`http://localhost:5173`), `AUTH_COOKIE_SECURE`
    (false), `AUTH_COOKIE_SAMESITE` (Lax), `DIGEST_CRON`
    (`0 0 7 * * *`), `RESEND_API_KEY` (empty = dry-run), `RESEND_FROM`,
    `PORT` (8080), `FLYWAY_ENABLED` (true).
- Frontend build fails if `VITE_API_BASE_URL` is missing
  (`vite.config.ts` throws; `lib/apiClient.ts` throws too).

## 5. Error-handling design

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception to
`{"message":"..."}`:

| Exception | Status |
|---|---|
| `NotFoundException` | 404 |
| `BadRequestException`, `HttpMessageNotReadableException`, validation errors | 400 |
| `ConflictException` | 409 |
| `TooManyRequestsException` | 429 |
| `BadCredentialsException` | 401 |
| `AccessDeniedException` | 403 (handled by Spring Security / advice) |
| Unauthenticated access | 401 `{"message":"Unauthorized"}` (security entry point) |

## 6. Time and money conventions

- **Dates**: `LocalDate` (item dates), `Instant` (created/updated/logged/sent
  timestamps, PostgreSQL `timestamptz`).
- **Expiry logic**: server-local `LocalDate.now()`.
- **Digest deduplication**: the **UTC** day of `sent_at` (SQL
  `date_trunc('day', sent_at, 'UTC')` matching
  `LocalDate.now(ZoneOffset.UTC)` in `NotificationRecorder`).
- **Money**: user-entered `estimatedCostLost` (`numeric(10,2)`); formatted on
  the frontend as INR (`lib/money.ts`).

## 7. Frontend bundle design

`vite.config.ts` splits manual chunks for cache efficiency:

- `charts`: chart.js + react-chartjs-2
- `scanner`: html5-qrcode (also dynamically imported on demand by
  `BarcodeScannerInput`)
- `radix`: all Radix UI packages

`html5-qrcode` is only imported when the user actually scans (dynamic
`import('html5-qrcode')`), keeping the main bundle small.