# 24 — Troubleshooting

## Startup failures

| Symptom | Likely cause | Resolution |
|---|---|---|
| "JWT_SECRET is not configured — refusing to start with an empty secret" | `JWT_SECRET` missing/blank | Set a ≥ 32-byte secret in the env/secret store |
| Fails on datasource initialization | `DB_URL` missing or not `jdbc:postgresql:` prefixed | Prefix the Supabase string with `jdbc:`; add `?sslmode=require`; check `DB_PASSWORD` |
| Flyway errors on boot | Schema drift or manual edits | Never edit production tables by hand; fix drift or use a new migration; `FLYWAY_ENABLED` toggles migrations |
| Wrong port on Render | `PORT` not injected | Render injects `PORT`; app binds `server.port=${PORT:8080}` |

## Authentication issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| 401 on `/auth/refresh` | Cookie missing (JS can't read it by design) or generation rotated | Re-login; verify cookie path is `/api/auth`; verify `AUTH_COOKIE_SECURE` matches the scheme (HTTPS → true) |
| Refresh cookie not sent cross-site | `SameSite=None` not set, or Secure missing | Set `AUTH_COOKIE_SAMESITE=None` + `AUTH_COOKIE_SECURE=true` only when frontend/API are on different registrable domains |
| Constant 401s after login in the browser | CORS origin not allowlisted | Add the exact origin to `CORS_ALLOWED_ORIGINS` and redeploy |
| 429 on login/register | Rate limit (5/3 per min per IP) | Wait a minute; check that `X-Forwarded-For` is populated by the proxy |

## Digest / email issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| No emails at all | `RESEND_API_KEY` unset → dry-run | Set the key; watch the log for `[digest dry-run]` |
| Emails failing at Resend | Sender unverified / quota | Verify the `RESEND_FROM` domain in Resend; check Resend dashboard |
| Duplicate digest emails | Should not happen | Verify `notifications_dedup_idx` exists; ensure the job runs on one instance |
| Digest timing wrong | `DIGEST_CRON` | Adjust the cron env var (default `0 0 7 * * *`), redeploy |

## Item / waste issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| Item gone after "mark wasted" | **By design** — waste deletes the item | History is in `waste_log` (snapshot name/unit) |
| 403 on item access | Foreign item / wrong account | Only the owner can access an item; logout and log in as the owner |
| Delete returns 404 | Already deleted (e.g. by waste) | Expected; the smoke test asserts this |
| Quantity validation errors | `quantityWasted` ≤ 0 or > item quantity | Re-enter within `0 < qty ≤ item qty` |

## Barcode issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| "Invalid barcode format" | Code not 8–14 digits | Check the scanned/typed value |
| "Product not found for this barcode" | Open Food Facts has no entry (`status != 1`) | Add the item manually |
| "Barcode service unavailable right now" | OFF timeout (10 s) or network | Retry; the Postgres cache prevents repeat external calls |
| Camera won't start | Permission / no camera / in use | The UI maps these to friendly messages; allow camera access |

## Frontend issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| Deep link 404 on Vercel | Missing `vercel.json` rewrite | Ensure `/(.*)` → `/index.html` is deployed |
| Build fails with "VITE_API_BASE_URL is not set" | Missing env var at build time | Set it on Vercel (or in `.env` locally) |
| "Something went wrong" toasts | Network/API errors | Check the API base URL; check `/api/health` |

## Smoke test issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| Script hangs | PS 5.1 DOM parsing on Cloudflare endpoints | Script already uses `-UseBasicParsing`; update to PowerShell 7 if issues persist |
| List/Delete assertions after waste | Expected (item deleted) | Script asserts absence + 404 by design |
| Stale token errors | `WebSession` header replay | Script clears `Session.Headers` per request |

## Database issues

| Symptom | Likely cause | Resolution |
|---|---|---|
| Missing tables on a fresh DB | Flyway not run | `FLYWAY_ENABLED=true`; check `flyway_schema_history` |
| Schema mismatch between test and prod | H2 schema vs Flyway | The H2 schema is test-only; production is Flyway-managed |
| Slow item listing | Missing index | `items_owner_expiry_idx (owner_id, expiry_date)` is created by V1 |