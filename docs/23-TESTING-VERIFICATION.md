# 23 — Testing & Verification

## Backend test suite

Command: `mvn -B -ntp test` → **146 tests, 0 failures** (verified run).

| Test class | Covers |
|---|---|
| `AuthServiceTest` | register/login/refresh/revoke, duplicate email, bad credentials, generation checks |
| `JwtServiceTest` | token creation/parsing, `typ` separation, generation claim |
| `AuthRateLimiterTest` | per-IP limits and windows |
| `SecurityMvcTest` | filter chain: permitted paths, protected paths, 401 entry point |
| `SecurityMvcSecureCookieTest` | cookie flags (`Secure`/SameSite) |
| `ItemServiceTest` | CRUD, filters, sorting, ownership 403s, waste validation |
| `ItemStatusServiceTest` | status boundaries |
| `MarkWastedConcurrencyTest` | pessimistic-lock serialization of concurrent waste |
| `BarcodeServiceTest` | validation, cache hit/miss, OFF failures |
| `ProductCacheWriterTest` | cache write success/failure isolation |
| `ResendClientTest` | send success/failure, dry-run, invalid recipients |
| `ExpiryDigestServiceTest` | planning, per-user isolation, idempotency, failure policy |
| `NotificationRecorderTest` | pre-check + record behavior, unique-index races |
| `AnalyticsServiceTest` | monthly aggregation |
| `UserRoleMappingTest`, `NotificationTypeMappingTest` | enum ↔ DB mapping |
| `PantryTrackerApplicationTest` | Spring context loads |

### Test infrastructure

- **H2** (test scope) with `src/test/resources/schema-h2.sql` mirroring the
  entity model — used by integration/concurrency tests. Production schema is
  Flyway + Postgres only.
- **Mockito** runs as a Java agent via Surefire `argLine`
  (`pom.xml`), required on JDK 21+ for the inline mock maker.
- **byte-buddy 1.18.10** pinned for JDK 21+ class-file compatibility.

## Frontend verification

- No unit-test framework is configured; the build is the gate:
  `npm run build` = `tsc -b && vite build` (type-check + production bundle).
- Verified: build succeeded (Vite 6.4.3, 2105 modules, ~4.4 s).

## Production smoke test (`production-smoke-test.ps1`)

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\production-smoke-test.ps1`

15 checks against the live API (`https://smart-expiry-tracker-pn5i.onrender.com/api`):

| # | Check | Expects |
|---|---|---|
| 1 | Health | 200 `{"status":"UP"}` |
| 2 | Register (unique `smoke.<guid>@example.com`) | 201 + access token |
| 3 | Login | 200 + new token |
| 4 | Refresh (cookie) | 200 + rotated token |
| 5 | Authenticated `/auth/me` | 200, matching user |
| 6 | Categories | 200 with seeded categories |
| 7 | Create item | 201 |
| 8 | Get item | 200 |
| 9 | Update item | 200 |
| 10 | Mark wasted | 200 (item deleted by design) |
| 11 | List items | 200, item **absent** |
| 12 | Delete item | 404 (already deleted) |
| 13 | Unauthenticated `/auth/me` | 401 |
| 14 | Logout | 200 |
| 15 | Refresh after logout | 401 (revoked) |

**Final run: 15/15 PASS.**

### Smoke-test safety properties

- Random credentials (`smoke.<guid>@example.com` + 40-char password) exist
  only in memory — never printed.
- Output sanitized to endpoint + HTTP status + server `message` (no tokens,
  cookies, or passwords).
- Only the throwaway account's data is touched; it is cleaned up during the
  run.

### PowerShell 5.1 quirks handled by the script

| Quirk | Handling |
|---|---|
| Default `Invoke-WebRequest` DOM parsing hangs on Cloudflare-fronted JSON endpoints | `-UseBasicParsing` + `TimeoutSec 30` |
| `ConvertFrom-Json` double-wraps top-level arrays (`[[{…}]]`) | `ConvertFrom-ApiJson` unwraps nested arrays |
| `WebSession` replays the stale `Authorization` header | `$script:Session.Headers.Clear()` before every request |
| `markWasted` deletes the item | List asserts absence; Delete expects 404 |

## End-to-end verification summary

| Check | Result |
|---|---|
| `mvn -B -ntp test` | 146/146 pass |
| Docker build | success |
| Container run + Flyway V1–V4 + health | UP |
| `npm run build` | success (2105 modules) |
| Production smoke test | 15/15 PASS |
| Live health endpoint | `{"status":"UP"}` |

## Not verified from the current source

- Load/performance testing
- Browser matrix testing (only modern evergreen assumed)
- E2E UI automation (e.g. Playwright) — none configured
- Email deliverability (inbox vs spam)