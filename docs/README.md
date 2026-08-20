# Documentation Index — Smart Expiry & Pantry Waste Tracker

Complete, code-grounded documentation for the project. Every document is
derived from the actual source code, configuration, migrations, and verified
deployments. Items that cannot be verified from the repository are explicitly
marked "Not verified from the current source."

## Master document

| File | Description |
|---|---|
| [SMART-EXPIRY-TRACKER-COMPLETE-DOCUMENTATION.md](./SMART-EXPIRY-TRACKER-COMPLETE-DOCUMENTATION.md) | **Master document** — all sections combined in one file (overview → requirements → architecture → API → security → business logic → deployment → operations → release). Start here. |

## Individual documents

| # | File | Contents |
|---|---|---|
| 01 | [01-PROJECT-OVERVIEW.md](./01-PROJECT-OVERVIEW.md) | What the project does, problem, target users, scope |
| 02 | [02-PROBLEM-STATEMENT.md](./02-PROBLEM-STATEMENT.md) | Problem context and success criteria |
| 03 | [03-REQUIREMENTS.md](./03-REQUIREMENTS.md) | Functional + non-functional requirements tables |
| 04 | [04-FEATURES.md](./04-FEATURES.md) | Verified feature list by category |
| 05 | [05-TECHNOLOGY-STACK.md](./05-TECHNOLOGY-STACK.md) | Frontend/backend/DB/deployment versions |
| 06 | [06-SYSTEM-ARCHITECTURE.md](./06-SYSTEM-ARCHITECTURE.md) | Context/container/sequence diagrams |
| 07 | [07-SYSTEM-DESIGN.md](./07-SYSTEM-DESIGN.md) | Layers, concurrency, config, errors, conventions |
| 08 | [08-FRONTEND-ARCHITECTURE.md](./08-FRONTEND-ARCHITECTURE.md) | Routing, auth state, API client, forms, charts, barcode |
| 09 | [09-BACKEND-ARCHITECTURE.md](./09-BACKEND-ARCHITECTURE.md) | Packages, security chain, services, transactions |
| 10 | [10-DATABASE-DESIGN.md](./10-DATABASE-DESIGN.md) | Tables, columns, constraints, indexes, migrations |
| 11 | [11-ER-DIAGRAM.md](./11-ER-DIAGRAM.md) | Mermaid ER diagram + relationships |
| 12 | [12-API-DOCUMENTATION.md](./12-API-DOCUMENTATION.md) | Every endpoint, request/response, error map |
| 13 | [13-AUTHENTICATION-SECURITY.md](./13-AUTHENTICATION-SECURITY.md) | JWT, rotation, cookies, CORS, rate limits, security controls |
| 14 | [14-BUSINESS-LOGIC.md](./14-BUSINESS-LOGIC.md) | Status, listing, waste, digest, barcode, validation |
| 15 | [15-ITEM-LIFECYCLE.md](./15-ITEM-LIFECYCLE.md) | State diagram + transitions |
| 16 | [16-WASTE-MANAGEMENT.md](./16-WASTE-MANAGEMENT.md) | Mark-wasted, snapshots, history, concurrency |
| 17 | [17-ANALYTICS-DESIGN.md](./17-ANALYTICS-DESIGN.md) | Monthly waste algorithm + frontend charts |
| 18 | [18-BARCODE-SYSTEM.md](./18-BARCODE-SYSTEM.md) | Scanning, validation, Open Food Facts, cache |
| 19 | [19-NOTIFICATION-EMAIL-SYSTEM.md](./19-NOTIFICATION-EMAIL-SYSTEM.md) | Digest job, Resend, idempotency, admin trigger |
| 20 | [20-DOCKER-ARCHITECTURE.md](./20-DOCKER-ARCHITECTURE.md) | Multi-stage Dockerfile, build vs runtime |
| 21 | [21-DEPLOYMENT-ARCHITECTURE.md](./21-DEPLOYMENT-ARCHITECTURE.md) | Render, Vercel, Supabase, URLs, vercel.json |
| 22 | [22-ENVIRONMENT-CONFIGURATION.md](./22-ENVIRONMENT-CONFIGURATION.md) | All env vars (names + purpose only) |
| 23 | [23-TESTING-VERIFICATION.md](./23-TESTING-VERIFICATION.md) | 146 tests, build gates, smoke test (15/15 PASS) |
| 24 | [24-TROUBLESHOOTING.md](./24-TROUBLESHOOTING.md) | Symptom → cause → fix tables |
| 25 | [25-OPERATIONS-RUNBOOK.md](./25-OPERATIONS-RUNBOOK.md) | Daily ops, deploys, schema changes, secret rotation |
| 26 | [26-RELEASE-CHECKLIST.md](./26-RELEASE-CHECKLIST.md) | Pre/post-deploy verification |
| 27 | [27-FUTURE-ENHANCEMENTS.md](./27-FUTURE-ENHANCEMENTS.md) | Proposals (none implemented) |

## Security note

No passwords, secrets, tokens, cookies, or credentials are documented
anywhere — only environment variable **names** and their purpose.

## Related files

| File | Purpose |
|---|---|
| `../README.md` | GitHub landing page |
| `../production-smoke-test.ps1` | 15-step production smoke test (15/15 PASS) |
| `../vercel.json` | SPA rewrite for Vercel |
| `../backend/Dockerfile` | Production image (Render) |
| `../backend/src/main/resources/db/migration/` | Flyway V1–V4 |