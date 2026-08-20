# 02 — Problem Statement

## Context

Household food waste is a widespread practical problem. In most homes the
failure mode is not lack of food, but lack of visibility: people do not know
what they already own, when it expires, and what they threw away last month
(and what that cost).

## Core problems addressed

### 1. Expiry dates are invisible
Once food or medicine is in the pantry, its expiry date is out of sight. There
is no automatic signal until the item is already spoiled. Manual reminders
(writing dates on packages, sticky notes) do not scale and are easily
forgotten.

### 2. One warning window does not fit all items
A single global "warn 3 days before expiry" rule is wrong for both extremes:
perishables spoil in days, while medicines remain usable for months. The
warning window must be a property of the item's **category**.

### 3. Users are not told about their own items only
Any email-based warning system must be strictly per-user: one user's pantry
data must never appear in another user's email.

### 4. Waste is never measured
Users know they "throw things away sometimes" but cannot see the scale of the
problem. Without a recorded, quantifiable metric (items wasted, estimated
money lost, per month) there is no motivation and no way to measure
improvement.

### 5. Data entry is friction
Manually typing every product name is tedious. Barcode scanning removes the
friction — but the client must not depend on third-party API availability,
CORS, or rate limits; lookups should be validated and cached.

### 6. Privacy and security
Pantry data is personal. The system must guarantee that a user can never read
or mutate another user's items, and that credentials and tokens are handled
securely.

## Statement

> Build a web-based pantry tracker that lets each household record its items,
> automatically classifies them as safe / expiring / expired using
> per-category thresholds, emails each user a daily digest of only their own
> at-risk items, records thrown-away items with estimated cost, and presents
> monthly waste analytics — all with a secure, per-user data model and
> minimal data-entry friction via barcode scanning.

## Success criteria (as implemented)

| Criterion | Verification |
|---|---|
| Item statuses are correct per category thresholds | `ItemStatusService` + `ItemStatusServiceTest` |
| Daily digest is per-user and idempotent | `ExpiryDigestService`, unique index `notifications_dedup_idx` |
| Waste events survive item deletion | Snapshot columns in `waste_log` (Flyway V3) |
| Users cannot access each other's data | `OwnershipGuard` + ownership-scoped queries |
| Barcode lookups are fast after the first scan | `product_cache` table |
| The full chain works in production | `production-smoke-test.ps1` — 15/15 PASS |