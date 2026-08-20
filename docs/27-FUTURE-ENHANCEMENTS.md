# 27 — Future Enhancements

All items below are proposals based on the current architecture — none are
implemented today.

## Product / feature

| Idea | Why it fits the architecture |
|---|---|
| Notification preferences | The `notifications.channel` column already exists (`email` default); add opt-out, per-user digest time, or in-app inbox |
| Push notifications / SMS | New `channel` values plug into the same `Notification` model |
| Category management UI | Categories are seed data today; an admin CRUD API (`/api/admin/categories`) is the natural extension |
| Item images & shopping lists | Extend the `items` model; the frontend already has modal/dialog primitives |
| Restore items from waste | `waste_log` keeps snapshots — a "re-add" action could recreate the item |
| Import/export (CSV) | Backend CSV endpoints + frontend buttons |
| Multi-user households | Would require a new `households`/`memberships` model + authorization updates (currently single-owner) |

## Engineering

| Idea | Why it fits the architecture |
|---|---|
| Distributed lock for the digest job | Currently safe by unique index, but a leader lease (e.g. ShedLock) would prevent duplicate *emails* when scaling to multiple instances |
| Prometheus/Micrometer metrics | The app is stateless and already health-checked; add metrics + alerting on health and digest failures |
| E2E tests (Playwright) | Complements the backend suite and the production smoke test with real UI flows |
| Load/performance tests | NFRs for latency/load are currently unverified |
| Refresh-token family/blacklist tracking | Generation counter is minimal; a token family table would support advanced replay detection |
| Pagination for `GET /api/items` | Currently returns the full user list (filters in memory); pageable queries scale better for large pantries |
| Email verification / password reset | Auth is email+password only; adding verification tokens extends `JwtService` patterns |
| Rate-limit persistence | `AuthRateLimiter` is in-memory; a Redis/shared store would work across instances |
| i18n | UI text is English-only; date/money formatters are already localized (INR) |

## Operations

| Idea | Status |
|---|---|
| CI pipeline (GitHub Actions or platform CI) | Not present in the repo today; would run `mvn test` + `npm run build` + smoke test on deploy |
| Staging environment | A second Render service + Vercel preview deployment |
| Automated backup verification | Supabase platform defaults exist; explicit restore drills are not configured |

## Explicitly not planned (by current design)

- Supabase Auth / RLS / Edge Functions (deliberately disabled).
- Local docker-compose/nginx serving (removed by project decision).