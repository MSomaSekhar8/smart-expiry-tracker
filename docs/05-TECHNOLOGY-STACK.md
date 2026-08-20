# 05 — Technology Stack

All versions are taken from `package.json`, `package-lock.json` (via the
built bundle), `pom.xml`, and the repository configuration.

## Frontend

| Technology | Version | Purpose | Verified in |
|---|---|---|---|
| React | 19.0.0 | UI library | `package.json` |
| TypeScript | ~5.7.2 | Typed language | `package.json` |
| Vite | ^6.0.5 (built with 6.4.3) | Dev server + bundler | `package.json`, build log |
| Tailwind CSS | ^4.0.0 | Styling (via `@tailwindcss/vite`) | `package.json`, `vite.config.ts` |
| react-router-dom | ^7.1.1 | Client-side routing (BrowserRouter) | `package.json`, `src/main.tsx` |
| axios | ^1.7.9 | HTTP client with interceptors | `package.json`, `src/lib/apiClient.ts` |
| chart.js / react-chartjs-2 | ^4.4.7 / ^5.3.0 | Charts (line + doughnut) | `package.json`, `src/lib/chart.ts` |
| html5-qrcode | ^2.3.8 | Camera + image barcode scanning | `package.json`, `BarcodeScannerInput.tsx` |
| date-fns | ^4.1.0 | Date formatting | `package.json`, `src/lib/dates.ts` |
| sonner | ^1.7.2 | Toast notifications | `package.json` |
| lucide-react | ^0.470.0 | Icons | `package.json` |
| Radix UI (dialog, dropdown-menu, select, label, slot, avatar) | 1.x | Accessible UI primitives | `package.json` |
| class-variance-authority / clsx / tailwind-merge | ^0.7.1 / ^2.1.1 / ^2.6.0 | Styling utilities | `package.json`, `src/lib/utils.ts` |
| @vitejs/plugin-react | ^4.3.4 | React fast-refresh in Vite | `package.json` |

## Backend

| Technology | Version | Purpose | Verified in |
|---|---|---|---|
| Java | 21 | Runtime language | `pom.xml` (`java.version`) |
| Spring Boot | 3.5.16 | Framework (Web, Security, Data JPA, Validation, Scheduling, WebFlux) | `pom.xml` (parent) |
| Maven | 3.9+ | Build tool | `pom.xml`, README prerequisites |
| JJWT (api/impl/jackson) | 0.12.6 | JWT creation/parsing (HS256) | `pom.xml` (`jjwt.version`) |
| PostgreSQL driver | Boot-managed | JDBC to Supabase Postgres | `pom.xml` |
| Flyway (core + postgresql) | Boot-managed | Versioned schema migrations | `pom.xml` |
| H2 | test scope | In-memory DB for concurrency tests | `pom.xml`, `schema-h2.sql` |
| byte-buddy | 1.18.10 (pinned) | Mockito agent compatibility on JDK 21+ | `pom.xml` |
| spring-security-test | Boot-managed | Security tests | `pom.xml` |

## Database

| Technology | Version | Purpose | Verified in |
|---|---|---|---|
| PostgreSQL | 15 (local CLI default), 16+ hosted | Primary datastore | `supabase/config.toml`, README |
| Flyway | Boot-managed | Schema ownership (V1–V4) | `backend/src/main/resources/db/migration` |

## Deployment & infrastructure

| Technology | Purpose | Verified in |
|---|---|---|
| Docker (multi-stage) | Production image for the backend | `backend/Dockerfile` |
| Render | Hosts the API (`smart-expiry-tracker-pn5i.onrender.com`) | Deployment URL (from smoke test + README) |
| Vercel | Hosts the SPA (`smart-expiry-tracker-kappa.vercel.app`) | Deployment URL + `vercel.json` |
| Supabase | Managed PostgreSQL only (no Auth/RLS/Edge) | `supabase/config.toml`, README |
| Resend | Transactional email API | `ResendClient.java` |
| Open Food Facts | Barcode product data | `WebClientConfig.java` |

## Verification tooling

| Tool | Purpose | Verified in |
|---|---|---|
| Maven Surefire (+ Mockito javaagent) | Backend tests (146) | `pom.xml`, `mvn -B -ntp test` run |
| `tsc -b && vite build` | Frontend type-check + build gate | `package.json` |
| `production-smoke-test.ps1` | Production end-to-end verification (15 checks) | repo root; final run 15/15 PASS |

## GitHub / CI

- No `.github/` directory, no CI workflow files, no Render/Vercel config
  files in the repository.
- Not verified from the current source: whether CI/CD is configured on the
  platforms; deploy settings such as exact build commands and env-var
  wiring on Render/Vercel dashboards are platform configuration, not
  repository files.