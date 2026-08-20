# Smart Expiry & Pantry Waste Tracker

Track what's in your pantry, get an email before things expire, and see exactly
how much money you're wasting on spoiled food and expired medicine.

## Features

- **Item tracking** — add, edit, and delete items with expiry dates, quantities,
  barcodes, and per-category shelf-life defaults.
- **Expiry status** — items are flagged as expiring soon or expired based on the
  category's warning threshold (e.g. medicines warn 7 days out, perishables 1 day).
- **Daily digest email** — a scheduled job (07:00) emails every user a
  summary of that user's own items expiring soon (one email per user, sent
  to the user's registered address). Idempotent: a unique database index
  guarantees each item is only notified once per day.
- **Barcode lookup** — scan or type a barcode; product info is fetched from
  Open Food Facts and cached server-side in Postgres.
- **Waste analytics** — log thrown-away items with estimated cost lost; a
  historical snapshot (item name + unit) is kept even after the item is
  deleted, so the dashboard and analytics pages keep showing what was wasted.
- **Authentication** — email/password registration and login with JWT access +
  refresh tokens (BCrypt-hashed passwords).

## Architecture

- **Backend** — Java 21 + Spring Boot 3.4 (Web, Security, Data JPA, Validation,
  Scheduling, WebFlux) in `backend/`.
- **Database** — Supabase is used **only as managed Postgres**. No Supabase Auth,
  no RLS, no Edge Functions. The schema is owned and versioned by **Flyway**
  (`backend/src/main/resources/db/migration`), and authorization happens in the
  Spring service layer (ownership checks + `@PreAuthorize`).
- **Scheduled jobs** — Spring `@Scheduled` runs the daily digest via Resend.
- **Frontend** — React 19 + Vite + TypeScript + Tailwind v4 + shadcn-style
  components + Chart.js in the repo root. Talks to the Spring API with axios
  (JWT in the `Authorization` header, automatic single-flight refresh on 401).

## Project layout

```
smart-expiry-tracker/
├── backend/                        # Spring Boot application
│   └── src/main/
│       ├── java/com/pantrytracker/
│       │   ├── auth/               # JWT issue/validate, filter, login/register/refresh
│       │   ├── user/               # users entity (roles USER / ADMIN)
│       │   ├── item/               # items, expiry status logic
│       │   ├── category/           # per-category shelf-life and warning thresholds
│       │   ├── wastelog/           # waste logging for analytics
│       │   ├── analytics/          # dashboard/analytics queries
│       │   ├── notification/       # daily digest email job
│       │   ├── barcode/            # Open Food Facts lookup + product_cache
│       │   ├── email/              # Resend client
│       │   ├── common/             # exceptions, ownership guard, API envelope
│       │   └── config/             # security, CORS, WebClient
│       └── resources/db/migration/ # Flyway migrations (V1__init, V2__seed)
├── src/                            # React frontend
│   ├── pages/                      # Dashboard, ItemList, Analytics, Auth, Settings
│   ├── components/                 # UI + feature components
│   ├── context/                    # auth + theme state
│   ├── hooks/                      # data fetching
│   └── lib/                        # API client, types, helpers
└── supabase/                       # local Supabase config (no auth used)
```

## Getting started

### Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+ (for the frontend)
- A Supabase project (or any PostgreSQL 16+ database)

### Backend

```bash
cd backend
# set required env vars
$env:DB_URL="jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="<password>"
$env:JWT_SECRET="<at-least-32-random-bytes>"
mvn spring-boot:run
```

The app refuses to start without `DB_URL` (must be a JDBC PostgreSQL URL —
for Supabase, prefix the connection string with `jdbc:`), `DB_PASSWORD`, or
`JWT_SECRET` (no built-in defaults).

Flyway creates the schema on first startup (baseline version 0, then
`V1__init.sql`, `V2__seed_categories.sql`, `V3__add_waste_snapshot.sql`).

### Frontend

```bash
npm install
# create .env from .env.example (VITE_API_BASE_URL=http://localhost:8080/api)
npm run dev
```

The app expects the API at the URL given by `VITE_API_BASE_URL` (default
`http://localhost:8080/api` in the local `.env`). The build fails if
`VITE_API_BASE_URL` is not set — set it to your deployed backend URL in CI/CD.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | — (required, no default) | JDBC URL of the Postgres database. Must start with `jdbc:postgresql:` — Supabase connection strings (`postgresql://...`) need the `jdbc:` prefix, e.g. `jdbc:postgresql://<host>:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | — (required, no default) | Database password; set in your deployment's secret store |
| `JWT_SECRET` | — (required, no default) | HS256 signing secret, at least 32 bytes; the app fails at startup if missing |
| `JWT_ACCESS_TTL_MINUTES` | `60` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | `14` | Refresh token lifetime |
| `RESEND_API_KEY` | — | Resend API key for digest emails (missing = digest dry-run, nothing sent) |
| `RESEND_FROM` | `Pantry Tracker <onboarding@resend.dev>` | Email sender |
| `DIGEST_CRON` | `0 0 7 * * *` | Daily digest schedule |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed frontend origins (comma-separated allowlist) |
| `AUTH_COOKIE_SECURE` | `false` | `Secure` on the HttpOnly refresh cookie. **MUST be `true` in production (HTTPS)**; off by default so local HTTP dev works |
| `AUTH_COOKIE_SAMESITE` | `Lax` | SameSite for the refresh cookie. `Lax` covers localhost and same-site production (e.g. `app.example.com` → `api.example.com`). Use `None` only if the frontend and API are on different registrable domains (requires `AUTH_COOKIE_SECURE=true`) |
| `PORT` | `8080` | Server port |
| `FLYWAY_ENABLED` | `true` | Toggle Flyway migrations |
| `VITE_API_BASE_URL` | — (required for the frontend build) | Backend base URL, e.g. `http://localhost:8080/api` locally or `https://api.example.com/api` in production; the build fails if unset |