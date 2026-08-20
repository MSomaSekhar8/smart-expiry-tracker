# 09 — Backend Architecture

## Packages (feature-oriented)

```
com.pantrytracker
├── auth/          AuthController, AuthService, JwtService, JwtAuthFilter,
│                  RefreshCookieService, AuthRateLimiter, AuthDtos, AuthenticatedUser
├── user/          User, UserRepository, UserRole
├── item/          Item, ItemRepository, ItemService, ItemController,
│                  ItemDtos, ItemStatusService, ItemStatus
├── category/      Category, CategoryRepository, CategoryController
├── wastelog/      WasteLog, WasteLogRepository, WasteLogController, WasteLogDtos
├── analytics/     AnalyticsService, AnalyticsController, AnalyticsDtos
├── notification/  ExpiryDigestService, ExpiryDigestJob, NotificationRecorder,
│                  Notification, NotificationRepository, NotificationType,
│                  ExpiryDigestTemplate, DigestController
├── barcode/       BarcodeService, BarcodeController, BarcodeDtos,
│                  ProductCache, ProductCacheRepository, ProductCacheWriter
├── email/         ResendClient
├── common/        ApiResponse, GlobalExceptionHandler, OwnershipGuard,
│                  BadRequestException, NotFoundException, ConflictException,
│                  TooManyRequestsException, HealthController
└── config/        SecurityConfig, CorsConfig, WebClientConfig
```

## Startup behavior

1. `PantryTrackerApplication` — `@SpringBootApplication` + `@EnableScheduling`.
2. `JwtService` constructor throws if `JWT_SECRET` is blank (fail-fast).
3. Flyway applies V1–V4 against `${DB_URL}` (baseline 0,
   `FLYWAY_ENABLED=${FLYWAY_ENABLED:true}`). Missing `DB_URL`/`DB_PASSWORD`
   prevents startup.
4. `CorsConfig` registers the origin allowlist; `SecurityConfig` builds the
   stateless filter chain.

## Security chain (`config/SecurityConfig.java`)

- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`:
  parses the `Authorization: Bearer` token, requires `typ=access`, loads the
  user, sets `AuthenticatedUser(user)` as principal with `ROLE_<role>`.
- `permitAll`: `POST /api/auth/login|register|refresh|logout`,
  `GET /api/health`, `/error`.
- `/api/admin/**` requires `ROLE_ADMIN`.
- Everything else requires authentication; unauthenticated → 401
  `{"message":"Unauthorized"}`.
- Stateless (no HTTP sessions).

## Rate limiting (`auth/AuthRateLimiter.java`)

- Per-IP sliding windows: login 5/min, register 3/min, refresh 10/min.
- IP resolved from `X-Forwarded-For` (Render proxy).
- Exceeded → 429 `{"message":"Too many requests — try again later"}`.

## JWT (`auth/JwtService.java`)

- HS256, key from `JWT_SECRET` bytes (≥ 32 bytes).
- Access: `sub`=user UUID, `typ=access`, TTL `JWT_ACCESS_TTL_MINUTES` (60).
- Refresh: `sub`=user UUID, `typ=refresh`, `gen`=generation, TTL
  `JWT_REFRESH_TTL_DAYS` (14).
- `typ` separation prevents a refresh token from being used as a bearer
  credential.

## Services and transactions

| Service | Key methods | Transaction notes |
|---|---|---|
| `AuthService` | `register`, `login`, `refresh`, `revokeRefreshToken`, `me` | `refresh`/`revoke` lock the user row (`findByIdForUpdate`) |
| `ItemService` | `list`, `get`, `create`, `update`, `delete`, `markWasted` | `markWasted` uses `findOwnedForUpdate` (row lock) |
| `BarcodeService` | `lookup` | No transaction around the external call; cache write via `REQUIRES_NEW` |
| `AnalyticsService` | `monthlyWaste` | Read-only |
| `ExpiryDigestService` | `run` | No transaction during send; `NotificationRecorder` uses `REQUIRES_NEW` |

## Ownership enforcement

- `OwnershipGuard.requireOwner(ownerId, userId, resource)` throws
  `AccessDeniedException` → 403.
- Used by every item read/update/delete path; `markWasted` and refresh
  rotation scope the locking query by owner instead.

## Exception handling (`common/GlobalExceptionHandler.java`)

Maps `NotFoundException`→404, `BadRequestException`/`HttpMessageNotReadableException`/validation→400,
`ConflictException`→409, `TooManyRequestsException`→429,
`BadCredentialsException`→401 — all as `{"message":"..."}`.

## Scheduled jobs (`notification/ExpiryDigestJob.java`)

- `@Scheduled(cron = "${app.digest.cron}")`, default `0 0 7 * * *`.
- Calls `ExpiryDigestService.run()` (see `19-NOTIFICATION-EMAIL-SYSTEM.md`).

## External clients

- `config/WebClientConfig.java` — Open Food Facts `WebClient`
  (`https://world.openfoodfacts.org`, User-Agent
  `SmartExpiryTracker/0.1 (pantry-waste-tracker)`).
- `email/ResendClient.java` — `RestClient` to `https://api.resend.com`
  (5 s connect / 10 s read timeouts; dry-run when no API key).

## Testing setup

- Surefire runs with the Mockito inline mock-maker as a Java agent (JDK 21+
  requirement; see `pom.xml` argLine).
- Integration tests use H2 (`src/test/resources/schema-h2.sql`) with a
  schema mirroring the entity model so pessimistic-lock concurrency tests
  can run embedded.