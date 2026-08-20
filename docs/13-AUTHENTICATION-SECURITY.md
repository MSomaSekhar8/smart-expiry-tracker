# 13 — Authentication & Security

## Token model

| Token | Claims | TTL | Transport | Storage |
|---|---|---|---|---|
| Access JWT | `sub` (user UUID), `typ=access`, `iat`, `exp` | 60 min (`JWT_ACCESS_TTL_MINUTES`) | `Authorization: Bearer` header | browser memory only (`tokenStore`) |
| Refresh JWT | `sub`, `typ=refresh`, `gen` (generation), `iat`, `exp` | 14 days (`JWT_REFRESH_TTL_DAYS`) | `Set-Cookie: refresh_token; Path=/api/auth; HttpOnly` | cookie jar (JS cannot read) |

- Both HS256-signed with the key derived from `JWT_SECRET` (≥ 32 bytes).
- `JwtService` refuses to start without a secret.
- A refresh token can never authenticate a request (`typ` check in
  `parseAccessToken`), and an access token can never refresh (`typ` check in
  `parseRefreshToken`).

## Refresh-token rotation

1. Login/register issues a refresh token embedding the user's current
   `refresh_generation` (DB column, default 0).
2. `POST /auth/refresh`:
   - parses + validates the refresh token (401 on any failure);
   - loads the user with `SELECT … FOR UPDATE` (`UserRepository.findByIdForUpdate`);
   - rejects when the stored generation ≠ the token's generation;
   - increments the stored generation;
   - issues a new pair and re-sets the cookie.
3. The row lock serializes racing refreshes — a captured token cannot be
   replayed after the first use.
4. Logout bumps the generation for the user behind the presented cookie,
   revoking **every** outstanding refresh token for that account (other
   devices included); the cookie is cleared regardless of the outcome.

## Cookie strategy (`RefreshCookieService`)

| Attribute | Value |
|---|---|
| Name | `refresh_token` |
| Path | `/api/auth` (only sent to auth endpoints) |
| HttpOnly | true |
| Secure | `AUTH_COOKIE_SECURE` (must be `true` in production over HTTPS) |
| SameSite | `AUTH_COOKIE_SAMESITE` — `Lax` default; `None` only for cross-registrable-domain setups (requires Secure) |
| MaxAge | refresh TTL in days |

## Security controls (verified from code)

| Concern | Control |
|---|---|
| Password storage | BCrypt (`PasswordEncoder`) |
| Secret management | Env vars only; `.env*` git-ignored; `.env.example` holds placeholders |
| Stateless authN | `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter` |
| Authorization | `OwnershipGuard` (403 on foreign resources — no existence oracle), `@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/**` |
| CSRF | Stateless API; no session cookie; refresh endpoint protected by HttpOnly cookie + rate limit |
| XSS | Access token never in localStorage/sessionStorage; React escaping; email HTML escapes user item names (`ExpiryDigestTemplate.escape`) |
| CORS | Explicit allowlist (`CORS_ALLOWED_ORIGINS`), credentials allowed, restricted methods/headers, maxAge 3600 |
| Brute force | Rate limits: login 5/min, register 3/min, refresh 10/min per IP (via `X-Forwarded-For`) |
| Error leakage | Generic `Invalid email or password`; 403 instead of 404 for foreign resources |
| Input validation | Bean Validation on DTOs + regex checks (barcode `^\d{8,14}$` server-side, check digits client-side) |
| Dependency pinning | byte-buddy pinned to 1.18.10 (known CVE-tagged versions avoided) |

## CORS (`config/CorsConfig.java`)

- Origins: exact list from `CORS_ALLOWED_ORIGINS` (comma-separated). No
  wildcard — required because credentials are allowed.
- Methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Headers: `Authorization`, `Content-Type`.
- Max age: 3600 s.

## Frontend auth flow (`AuthContext` + `lib/apiClient.ts`)

- Access token lives only in memory; refresh cookie travels automatically
  (`withCredentials: true`).
- 401 handling is single-flight: concurrent 401s share one
  `POST /auth/refresh`; the original request is retried once; on refresh
  failure the token is cleared and `auth:unauthorized` signs the user out.
- Session restore: refresh → `/auth/me`.

## Rate limiting (`AuthRateLimiter`)

- Per-IP sliding window, 60 000 ms: `LOGIN_LIMIT=5`, `REGISTER_LIMIT=3`,
  `REFRESH_LIMIT=10`.
- IP from `X-Forwarded-For` first value (Render proxy).
- Response 429 `{"message":"Too many requests — try again later"}`.

## Not covered by this document

Actual secret values (passwords, JWT secrets, DB credentials, Resend API
keys, tokens, cookies) are intentionally not documented anywhere in this
repository.