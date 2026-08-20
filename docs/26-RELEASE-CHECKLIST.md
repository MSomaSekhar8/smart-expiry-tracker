# 26 — Release Checklist

## Before every release

- [ ] `cd backend && mvn -B -ntp test` — 146 tests, 0 failures.
- [ ] `npm run build` — TypeScript check + production bundle green.
- [ ] No secrets committed: `.env*` absent from git; `.env.example` holds
      placeholders only.
- [ ] `JWT_SECRET`, `DB_URL`, `DB_PASSWORD` configured in the deploy target
      (Render secret store) — names only, no values in the repo.
- [ ] `CORS_ALLOWED_ORIGINS` includes the exact frontend origin(s).
- [ ] `AUTH_COOKIE_SECURE=true` in production (HTTPS).
- [ ] `VITE_API_BASE_URL` set for the frontend build.
- [ ] New migrations (if any) verified locally against a fresh database.
- [ ] `flyway_schema_history` shows the expected version on the target DB.

## After deploy

- [ ] `GET /api/health` → `{"status":"UP"}`.
- [ ] Production smoke test → 15/15 PASS:
      `powershell -NoProfile -ExecutionPolicy Bypass -File .\production-smoke-test.ps1`
- [ ] Frontend loads; deep links (e.g. `/analytics`) do not 404.
- [ ] Register → login → add item → edit → mark wasted → analytics reflect
      the waste.
- [ ] Barcode flow: manual entry + lookup returns a product (or a clean
      "not found" message).
- [ ] Digest: with `RESEND_API_KEY` set, confirm one email per user per day;
      without it, confirm dry-run logs.
- [ ] Admin-only: "Test digest now" works from Settings.

## Smoke-test specific checks

- [ ] Register 201, login 200, refresh 200 (rotated cookie).
- [ ] `/auth/me` authenticated 200 / unauthenticated 401.
- [ ] Categories list seeded (grocery/medicine/perishable).
- [ ] Create → get → update → waste (item then absent) → delete 404.
- [ ] Logout 200; refresh after logout 401.

## Rollback plan

- [ ] Backend: redeploy the last known-good image/commit.
- [ ] Frontend: Vercel instant rollback to a previous deployment.
- [ ] Database: schema changes are additive migrations; do not manually edit
      tables (recovery via Supabase platform tools if needed).