# 22 — Environment Configuration

## Security rule

Only **variable names and their purposes** are documented here. Actual
values (passwords, JWT secrets, DB credentials, Resend API keys, tokens,
cookies) must never be committed or documented.

## Backend variables (read via `application.yml`)

| Variable | Default | Required | Purpose |
|---|---|---|---|
| `DB_URL` | — | **yes** (app refuses to start) | JDBC URL; must start with `jdbc:postgresql:` (Supabase strings need the prefix); add `?sslmode=require` |
| `DB_USERNAME` | `postgres` | no | Database user |
| `DB_PASSWORD` | — | **yes** | Database password (secret store) |
| `JWT_SECRET` | — | **yes** (fail-fast) | HS256 signing secret, ≥ 32 bytes |
| `JWT_ACCESS_TTL_MINUTES` | `60` | no | Access-token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `14` | no | Refresh-token lifetime |
| `RESEND_API_KEY` | (empty) | no | Resend API key; empty = digest dry-run (nothing sent) |
| `RESEND_FROM` | `Pantry Tracker <onboarding@resend.dev>` | no | Email sender |
| `DIGEST_CRON` | `0 0 7 * * *` | no | Daily digest schedule |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | no | Comma-separated origin allowlist |
| `AUTH_COOKIE_SECURE` | `false` | no | `Secure` on the refresh cookie; **must be `true` in production** |
| `AUTH_COOKIE_SAMESITE` | `Lax` | no | Cookie SameSite; `None` only for cross-domain setups (needs Secure) |
| `PORT` | `8080` | no | Server port (Render injects) |
| `FLYWAY_ENABLED` | `true` | no | Toggle Flyway migrations on startup |

## Frontend variables

| Variable | Required | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | **yes for build** | API base URL baked into the bundle; the build fails if unset (`vite.config.ts` throws; `lib/apiClient.ts` throws too) |

## `application.yml` highlights

```yaml
server.port:      ${PORT:8080}
spring.datasource:
  url:            ${DB_URL}
  username:       ${DB_USERNAME:postgres}
  password:       ${DB_PASSWORD}
  hikari:         (Boot defaults)
spring.jpa:
  hibernate.ddl-auto: none
  open-in-view:       false
spring.flyway:
  enabled:            ${FLYWAY_ENABLED:true}
  locations:          classpath:db/migration
  baseline-on-migrate: true
  baseline-version:   0
app.jwt:
  secret:           ${JWT_SECRET}
  access-ttl-minutes: ${JWT_ACCESS_TTL_MINUTES:60}
  refresh-ttl-days:  ${JWT_REFRESH_TTL_DAYS:14}
app.resend:
  api-key:          ${RESEND_API_KEY:}
  from:             ${RESEND_FROM:Pantry Tracker <onboarding@resend.dev>}
app.digest.cron:      ${DIGEST_CRON:0 0 7 * * *}
app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
app.auth:
  cookie-secure:    ${AUTH_COOKIE_SECURE:false}
  cookie-samesite:  ${AUTH_COOKIE_SAMESITE:Lax}
```

Note: there is **no** `application-prod.yml` in the repository — production
is configured purely through environment variables on Render.

## `.env.example` (tracked template)

Contains only placeholders (e.g. `JWT_SECRET=`, `DB_PASSWORD=`,
`RESEND_API_KEY=`) and the documented defaults. `.env*` files are
git-ignored (`.gitignore`), so real values never enter the repository.

## Local development

Backend:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:54322/postgres"   # local Supabase CLI Postgres
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="<local password>"
$env:JWT_SECRET="<at-least-32-random-bytes>"
mvn spring-boot:run
```

Frontend:
```powershell
Copy-Item .env.example .env   # VITE_API_BASE_URL=http://localhost:8080/api
npm install
npm run dev
```

## Fail-fast behavior (verified)

- Missing/blank `JWT_SECRET` → `IllegalStateException` at startup
  (`JwtService` constructor).
- Missing `DB_URL`/`DB_PASSWORD` → startup fails on datasource creation.
- Missing `VITE_API_BASE_URL` → frontend build fails.