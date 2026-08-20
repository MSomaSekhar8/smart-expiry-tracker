# 12 — API Documentation

Base URLs:
- Production: `https://smart-expiry-tracker-pn5i.onrender.com/api`
- Local: `http://localhost:8080/api`

All requests/responses are JSON (`Content-Type: application/json`).
Errors always use the shape `{"message": "..."}`.

---

## Public endpoints

### `GET /api/health`
Health check (used by Render health checks and the smoke test).

200:
```json
{"status":"UP"}
```

### `POST /api/auth/register`
Rate limit: 3/min/IP.

```json
{"email": "ada@example.com", "password": "correct-horse-9!", "displayName": "Ada"}
```

- 201 → `TokenResponse` (see below) + refresh cookie.
- 400 validation (`@Email`, `@NotBlank`, password ≥ 8 chars, display name ≤
  100).
- 409 `An account with this email already exists`.
- 429 rate limit.

### `POST /api/auth/login`
Rate limit: 5/min/IP.

```json
{"email": "ada@example.com", "password": "correct-horse-9!"}
```

- 200 → `TokenResponse` + refresh cookie.
- 401 `Invalid email or password`.

### `POST /api/auth/refresh`
Rate limit: 10/min/IP. No body — the refresh cookie is sent automatically.

- 200 → new `TokenResponse` + **rotated** refresh cookie.
- 401 when the cookie is missing/invalid/expired or the generation no longer
  matches.

### `POST /api/auth/logout`
No body. Revokes the refresh generation and clears the cookie.

- 200 → `{"message":"Logged out"}`. Invalid cookies are ignored; the cookie
  is cleared regardless.

---

## Authenticated endpoints (Bearer access token)

### `GET /api/auth/me`
Current user.

200:
```json
{"id":"…","email":"ada@example.com","displayName":"Ada","role":"USER"}
```

### `GET /api/categories`

200:
```json
[
  {"id":"…","name":"grocery","defaultShelfLifeDays":30,"warningThresholdDays":3},
  {"id":"…","name":"medicine","defaultShelfLifeDays":365,"warningThresholdDays":7},
  {"id":"…","name":"perishable","defaultShelfLifeDays":7,"warningThresholdDays":1}
]
```

### `GET /api/items`
Query params (all optional): `search` (name substring, case-insensitive),
`category` (UUID), `status` (`SAFE|EXPIRING|EXPIRED`, invalid → 400),
`sort` (`expiry|name|created|category`, default `expiry`),
`dir` (`asc|desc`).

200 → array of `ItemResponse`.

### `POST /api/items`
201 → `ItemResponse`.

```json
{
  "name": "Greek yogurt",
  "barcode": "5900951184062",
  "categoryId": "…",
  "quantity": 2,
  "unit": "cup",
  "purchaseDate": "2026-08-01",
  "expiryDate": "2026-08-14",
  "shelfLifeDays": 14,
  "notes": "Prefers low fat"
}
```

Validation: name required (≤ 200), category required (404 if unknown),
barcode ≤ 32, quantity ≥ 0 (≤ 8 integer / 2 fraction digits), unit ≤ 20,
shelf life ≥ 0, notes ≤ 5000.

### `GET /api/items/{id}`
200 → `ItemResponse`; 404 if missing; 403 if owned by another user.

### `PUT /api/items/{id}`
200 → `ItemResponse`. Same body/validation as create.

### `DELETE /api/items/{id}`
200 → `{"message":"Item deleted"}`; 404/403 as above. Waste history survives
via snapshot.

### `POST /api/items/{id}/waste`
Body (both optional):
```json
{"quantityWasted": 1.5, "estimatedCostLost": 3.25}
```

- `quantityWasted` defaults to the item's full quantity; must be > 0 and ≤
  item quantity (400 otherwise).
- On success the item is **deleted** and a `WasteLog` row is written.
  200 → `ItemResponse`; 403 foreign item; 404 missing item.

### `GET /api/waste-log?limit=20`
Recent waste entries for the caller, newest first. `limit` clamped to 1–100.

200:
```json
[
  {"id":"…","userId":"…","itemId":null,"itemName":"Greek yogurt",
   "quantityWasted":1.5,"unit":"cup","estimatedCostLost":3.25,"loggedAt":"…"}
]
```

### `GET /api/analytics/monthly-waste?months=6`
`months` clamped to 1–24 (default 6).

200:
```json
{
  "months": [
    {"month":"2026-03","totalItems":2,"wastedItems":1,"costLost":3.25}
  ],
  "totalCostLost": 3.25,
  "totalWasted": 1
}
```

### `GET /api/barcode/{code}`
Barcode must match `^\d{8,14}$`.

200:
```json
{"barcode":"5900951184062","name":"Greek yogurt","brand":"DairyCo","category":"dairy","cached":false}
```

400 for invalid format, `Barcode service unavailable right now`, or
`Product not found for this barcode`.

---

## Admin endpoints

### `POST /api/admin/digest/test`
`@PreAuthorize("hasRole('ADMIN')")` — runs the digest immediately.

200:
```json
{"expiringSoonCount":2,"expiredCount":1}
```

403 for non-admin users.

---

## `TokenResponse` (login/register/refresh body)

```json
{
  "accessToken": "eyJ…",
  "user": {"id":"…","email":"ada@example.com","displayName":"Ada","role":"USER"}
}
```

The refresh token is never in the body — it travels only in the HttpOnly
`refresh_token` cookie (`Path=/api/auth`).

---

## `ItemResponse`

```json
{
  "id": "…", "ownerId": "…", "name": "Greek yogurt", "barcode": "5900951184062",
  "categoryId": "…", "category": "grocery",
  "quantity": 2, "unit": "cup",
  "purchaseDate": "2026-08-01", "expiryDate": "2026-08-14",
  "shelfLifeDays": 14, "defaultShelfLifeDays": 30, "warningThresholdDays": 3,
  "notes": "Prefers low fat", "status": "SAFE", "daysUntilExpiry": -7,
  "createdAt": "…", "updatedAt": "…"
}
```

- `status`: `SAFE` | `EXPIRING` | `EXPIRED` (computed on read).
- `daysUntilExpiry`: `-1` when no expiry date; negative when already expired.

---

## Error status map

| Status | Trigger |
|---|---|
| 400 | Validation, invalid status filter, invalid barcode, bad quantity, unreadable JSON |
| 401 | Bad credentials, invalid/expired token, missing token on a protected route |
| 403 | Ownership violation; non-admin on `/api/admin/**` |
| 404 | Missing user/category/item; delete of an already-deleted item |
| 409 | Duplicate email on register |
| 429 | Auth rate limit exceeded |