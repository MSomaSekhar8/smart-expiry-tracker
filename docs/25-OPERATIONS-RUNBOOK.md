# 25 — Operations Runbook

## Daily operations

1. **Health check** — `GET https://smart-expiry-tracker-pn5i.onrender.com/api/health`
   → `{"status":"UP"}` (Render's health check does this automatically).
2. **Digest check** — at ~07:00 the digest job runs. Logs either normal
   sends or `[digest dry-run] N expiring, M expired — RESEND_API_KEY not set`.
3. **Smoke test** — run periodically:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\production-smoke-test.ps1
   ```
   Expected: 15/15 PASS.

## Deploying a backend change

1. `cd backend && mvn -B -ntp test` — must be green (146 tests).
2. Commit and push; Render builds the Docker image and deploys
   (Root Directory: `backend`).
3. Verify:
   - `/api/health` → UP;
   - `flyway_schema_history` on the database shows expected migrations;
   - smoke test 15/15.

## Deploying a frontend change

1. Ensure `VITE_API_BASE_URL` is set for the build (Vercel project env).
2. `npm run build` locally must pass.
3. Commit and push; Vercel builds `dist/` and deploys.
4. Verify: deep links resolve (no 404), login flow works, dashboard loads.

## Schema changes

1. Add a new migration `V5__*.sql` in
   `backend/src/main/resources/db/migration/` (never edit V1–V4).
2. Verify locally against a throwaway Postgres: Flyway applies V1→V5 cleanly.
3. Deploy the backend; confirm the new version in `flyway_schema_history`.
4. Note: `supabase db push` must NOT be used — Flyway owns the schema.

## Rotating secrets

1. Update the secret (e.g. `JWT_SECRET`, `DB_PASSWORD`) in Render's secret
   store.
2. Redeploy. All refresh tokens issued under the old key become invalid —
   users simply log in again. Keep the old value until the new instance is
   healthy.

## Manual digest run

1. As an ADMIN user, open Settings → "Test digest now" (or
   `POST /api/admin/digest/test` with an ADMIN bearer token).
2. Observe the `notifications` rows (one per item per UTC day per type) and
   Resend logs. The endpoint is idempotent.

## Monitoring

- Render dashboard: build status, live logs, health checks.
- Resend dashboard: email delivery/opens for digest sends.
- Supabase dashboard: database health, connections, usage.
- No additional monitoring/alerting is configured in this repository
  (metrics export is a future improvement).

## Incident response order

1. Check `/api/health` and Render logs.
2. Check the database connectivity (`DB_URL`/`DB_PASSWORD`).
3. Check digest logs if emails are missing.
4. Run the smoke test to isolate the failing layer.
5. Roll back by redeploying the last known-good commit if needed.

## Backup & recovery

- Database backups are a Supabase platform feature — recovery procedures
  are **Not verified from the current source** (no repo-level backup tooling
  exists).
- The application is stateless (no server-side files); redeploying the
  container restores the API; the database is the only durable state.