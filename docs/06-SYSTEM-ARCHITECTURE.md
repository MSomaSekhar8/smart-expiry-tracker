# 06 — System Architecture

## System context diagram

```mermaid
flowchart LR
    User["User<br/>(browser)"] -->|"HTTPS"| SPA["Smart Expiry & Pantry Waste Tracker<br/>Web application"]
    SPA -->|"HTTPS REST /api/*"| API["Spring Boot API<br/>(Render)"]
    API -->|"JDBC/TLS (sslmode=require)"| DB[("Supabase PostgreSQL<br/>(Flyway-managed schema)")]
    API -->|"POST /emails"| Resend["Resend API<br/>(daily digest)"]
    API -->|"GET /api/v2/product/{code}.json"| OFF["Open Food Facts API<br/>(barcode lookup)"]
    Resend -->|"email"| Inbox["User inbox"]
```

## Container architecture

```mermaid
flowchart TB
    subgraph Vercel["Vercel (static hosting)"]
        Assets["index.html + bundled JS/CSS<br/>(charts, scanner, radix chunks)"]
        Rewrite["vercel.json<br/>'/(.*)' → '/index.html'"]
    end
    subgraph Render["Render — Docker container (backend)"]
        Spring["Spring Boot app (jar)<br/>Tomcat on ${PORT}"]
        Security["JwtAuthFilter + SecurityConfig<br/>(stateless)"]
        Controllers["REST controllers<br/>(auth, items, categories, waste-log, analytics, barcode, admin digest)"]
        Services["Services<br/>(AuthService, ItemService, BarcodeService, ExpiryDigestService, ...)"]
        Repos["Spring Data JPA repositories<br/>(incl. pessimistic locks)"]
        Scheduler["@Scheduled ExpiryDigestJob<br/>(07:00 daily)"]
        Clients["ResendClient (RestClient)<br/>OpenFoodFacts WebClient"]
    end
    subgraph Supabase["Supabase (managed PostgreSQL)"]
        DB[("users · categories · items ·<br/>waste_log · notifications · product_cache")]
        Flyway["Flyway migrations V1–V4<br/>(applied at startup)"]
    end
    User["Browser"] -->|"HTTPS"| Vercel
    Vercel -->|"HTTPS + Bearer JWT + refresh cookie"| Spring
    Spring --> Controllers --> Services --> Repos
    Repos -->|"JDBC"| DB
    Flyway --> DB
    Scheduler --> Services
    Services --> Clients -->|"HTTPS"| Resend
    Services --> Clients -->|"HTTPS"| OFF
```

## Request flow (authenticated read)

```mermaid
sequenceDiagram
    participant B as Browser (SPA)
    participant V as Vercel (static)
    participant R as Render (API)
    participant DB as Supabase Postgres
    B->>V: GET /items (deep link or nav)
    V-->>B: index.html + JS (vercel.json rewrite)
    B->>R: GET /api/items (Authorization: Bearer access JWT)
    R->>R: JwtAuthFilter validates typ=access + signature + expiry
    R->>R: ItemService.list(userId) — ownership implicit via userId
    R->>DB: SELECT items WHERE owner_id=? (indexed)
    DB-->>R: rows
    R-->>B: JSON ItemResponse[] with status/daysUntilExpiry
```

## Request flow (auth)

```mermaid
sequenceDiagram
    participant B as Browser
    participant R as Render (API)
    participant DB as Supabase Postgres
    B->>R: POST /api/auth/login {email, password}
    R->>R: AuthRateLimiter (5/min/IP) → BCrypt verify
    R->>DB: find user by email (lowercased)
    R->>R: issue access JWT (typ=access, 60 min) + refresh JWT (typ=refresh, gen)
    R-->>B: 200 {accessToken, user} + Set-Cookie refresh_token (HttpOnly, Path=/api/auth)
    B->>R: POST /api/auth/refresh (cookie only)
    R->>DB: SELECT user FOR UPDATE; gen match? → gen++
    R-->>B: 200 new pair + rotated cookie
```

## Digest flow (daily job)

```mermaid
sequenceDiagram
    participant J as @Scheduled job (07:00)
    participant S as ExpiryDigestService
    participant DB as Supabase Postgres
    participant R as ResendClient
    participant M as User mailbox
    J->>S: run()
    S->>DB: load all items (owner + category eager)
    S->>S: group by owner; skip SAFE/no-expiry/already-notified
    loop per user
        S->>R: sendDigest(user, expiring, expired)
        alt send OK
            S->>DB: record Notification per item (REQUIRES_NEW, unique index guards races)
        else send failed
            S-->>S: not recorded → retried next run
        end
    end
    R-->>M: one HTML email per user
```

## Data flow (barcode lookup)

```mermaid
sequenceDiagram
    participant B as Browser
    participant R as Render (API)
    participant DB as Supabase Postgres
    participant O as Open Food Facts
    B->>B: validate check digits (EAN-13/EAN-8/UPC-A)
    B->>R: GET /api/barcode/{code}
    R->>DB: product_cache.findById(code)
    alt cache hit
        DB-->>R: payload (cached=true)
    else cache miss
        R->>O: GET /api/v2/product/{code}.json (10 s timeout)
        O-->>R: product payload (status=1)
        R->>DB: ProductCacheWriter.write (REQUIRES_NEW, failures swallowed)
        R-->>R: cached=false
    end
    R-->>B: {barcode, name, brand, category, cached}
```

## Architecture principles (from code)

1. **Stateless API** — JWT auth, no server sessions; any instance can serve
   any request.
2. **Ownership in the service layer** — `OwnershipGuard` + owner-scoped
   queries replace database RLS.
3. **Schema owned by Flyway** — `hibernate.ddl-auto=none`; migrations are the
   only way the schema changes.
4. **Single source of truth per concern** — statuses are computed on read,
   never stored; waste history is snapshotted at write time.
5. **Idempotent side effects** — digest emails and product-cache writes are
   safe under concurrency (unique index, `REQUIRES_NEW`).