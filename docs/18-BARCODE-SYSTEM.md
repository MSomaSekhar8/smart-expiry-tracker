# 18 — Barcode System

## Flow overview

```
Camera / image / manual entry
        │
        ▼
Client validation (check digits: EAN-13, EAN-8, UPC-A)
        │
        ▼
GET /api/barcode/{code}   (server validates ^\d{8,14}$)
        │
        ├── product_cache hit → return (cached: true)
        │
        └── miss → Open Food Facts (10 s timeout)
                     │
                     ├── status != 1 → 400 "Product not found for this barcode"
                     │
                     └── ok → cache write (REQUIRES_NEW, failures swallowed)
                              → return (cached: false)
```

## Frontend (`components/BarcodeScannerInput.tsx`)

### Camera scanning (html5-qrcode)

- Lazy-loaded (`import('html5-qrcode')`) only when the user starts a scan.
- Rear camera (`facingMode: 'environment'`), 10 fps, viewfinder box capped
  at 260×140 px.
- **Confirmation logic** to reject flaky reads:
  - each decoded value is validated (format-aware) and appended to a rolling
    window of the last 7 observations;
  - a candidate is accepted when it reaches 3 occurrences AND beats the
    runner-up by 2× (or has no runner-up);
  - a 3 s timer resets the window if confirmation never completes.
- On accept: input filled, camera stopped, server lookup fired, success
  toast shows the product name/brand.
- Cleanup on unmount (stop + clear the scanner instance).

### Image upload

- File picker (`accept="image/*"`, ≤ 10 MB, image type enforced).
- `Html5Qrcode.scanFileV2` decodes; result validated by its reported format.
- Friendly error messages ("No barcode found in this image…", "That file
  could not be read as an image…").

### Manual entry

- Numeric input (`inputMode="numeric"`), Enter or "Look up" triggers the
  server call.
- Client-side check-digit validation (`lib/barcodeValidation.ts`):
  - EAN-13: 13 digits, check digit matches `(10 - (Σ weights) % 10) % 10`
    with 3/1 weights from the right;
  - EAN-8 and UPC-A: same scheme at 8/12 digits;
  - other digit strings: allowed as `OTHER` when a format name is supplied,
    or when digits-only with unknown length.

### Error mapping

Camera errors are mapped to friendly messages: permission denied, no camera,
camera in use, over-constrained, generic.

## Backend (`BarcodeService`, `WebClientConfig`, `ProductCache*`)

- `GET /api/barcode/{code}` — barcode must match `^\d{8,14}$` (400
  otherwise).
- Cache-first: `ProductCacheRepository.findById(code)` (PK on barcode).
- Miss path:
  - `GET https://world.openfoodfacts.org/api/v2/product/{code}.json`
    via the `openFoodFactsWebClient` bean (custom User-Agent
    `SmartExpiryTracker/0.1 (pantry-waste-tracker)`; 10 s timeout);
  - network/parse failure → 400 `Barcode service unavailable right now`;
  - `payload.status != 1` → 400 `Product not found for this barcode`;
  - `ProductCacheWriter.write(code, payload)` persists the raw JSON in a
    `REQUIRES_NEW` transaction; failures are logged and swallowed so a
    cache hiccup never fails a successful lookup;
  - mapped to `LookupResult{barcode, name (product_name), brand (brands),
    category (first categories_tags, "en:" prefix stripped, dashes →
    spaces), cached}`.

## Why the browser never calls Open Food Facts directly

- Server-side proxy avoids CORS, public-API rate limits, and hides any
  future API keys.
- The Postgres cache turns every repeat scan into a local indexed read.
- The cache write is deliberately isolated from the lookup transaction.

## Auto-fill in the item form (`ItemFormFields`)

When a lookup succeeds:

- the item name is pre-filled if the name field is empty;
- the category is matched case-insensitively against the known categories
  (e.g. "dairy" → none of grocery/medicine/perishable unless matched) and
  the unit suggestion follows the category.

## Verified behavior

- `BarcodeServiceTest` — validation, cache hit/miss, OFF failure paths.
- `ProductCacheWriterTest` — write success/failure handling.
- Production smoke test does not cover barcode (external API dependency);
  barcode behavior is verified via unit tests. Not verified from the current
  source: Open Food Facts availability and response shape over time.