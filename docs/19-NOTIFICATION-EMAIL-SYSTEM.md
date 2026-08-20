# 19 — Notification & Email System

## Daily digest job

| Aspect | Value |
|---|---|
| Job | `ExpiryDigestJob` — `@Scheduled(cron = "${app.digest.cron}")` |
| Default cron | `0 0 7 * * *` (07:00 daily) |
| Entry point | `ExpiryDigestService.run()` |
| Idempotency | Per item per **UTC day** per type (unique DB index) |

## Digest pipeline (`ExpiryDigestService.run`)

```
buildPlans():
  items = findAllWithOwnerAndCategory()        # eager EntityGraph
  group by owner
  per user:
    skip items with no expiryDate
    skip status == SAFE
    skip alreadyNotifiedToday(item, type)      # read-only pre-check
    lines = DigestLine(name, expiryDate, type, daysLeft)

run():
  per plan:
    sent = ResendClient.sendDigest(user, expiringSoon, expired)   # outside any tx
    if sent:
      per item: NotificationRecorder.record(user, item, type)     # REQUIRES_NEW
```

Failure policy (verified from comments in `ExpiryDigestService`):

- an email is recorded **only after** a successful send;
- a failed send is never recorded → retried on the next run;
- one user's failure never aborts the rest of the batch;
- the batch is safe to run manually at any time (idempotent).

## Idempotency at the database level

- Unique index: `notifications_dedup_idx (item_id, type,
  date_trunc('day', sent_at, 'UTC'))`.
- `NotificationRecorder.alreadyNotifiedToday` pre-checks with the UTC-day
  boundary (`LocalDate.now(ZoneOffset.UTC)`).
- `NotificationRecorder.record` runs in `REQUIRES_NEW` and swallows
  `DataIntegrityViolationException` — a racing second insert returns `false`
  instead of aborting the run. Two scheduled runs racing can therefore never
  double-send.

## Email content (`ExpiryDigestTemplate`)

- Single HTML string, inline styles, no template engine.
- Header "Pantry Tracker — Your daily expiry digest".
- Sections: "Expiring soon" (amber heading) and "Already expired" (red
  heading), each a table of Item | Expires (with days-left).
- User-provided item names are HTML-escaped (`escape()`), and content is
  built per user — a user's pantry data is never mixed into another user's
  email.
- Footer: "Log in to review your pantry."

## Sending (`email/ResendClient.java`)

| Aspect | Value |
|---|---|
| API | `POST https://api.resend.com/emails` |
| Auth | `Authorization: Bearer <RESEND_API_KEY>` (never logged) |
| Timeouts | connect 5 s, read 10 s |
| Body | `{from: RESEND_FROM, to: [user.email], subject: "Pantry digest: N expiring, M expired", html}` |
| Dry-run | `RESEND_API_KEY` empty → logs only counts (`[digest dry-run] …`), no names/addresses/tokens |
| Guards | empty item lists → returns false (nothing sent); invalid stored email → warned + skipped |
| Failure | exceptions are caught → `false` (retried later) |

## Admin trigger

- `POST /api/admin/digest/test` — `@PreAuthorize("hasRole('ADMIN')")`.
- Returns `DigestReport{expiringSoonCount, expiredCount}`.
- Frontend: "Test digest now" button on the Settings page (admin-only card).

## Audit trail

Every successfully sent item-notification inserts a `notifications` row
(item, user, type, channel='email', sent_at) — usable for auditing and
future notification channels (the `channel` column is already present).

## Coverage

- `ExpiryDigestServiceTest` — planning, per-user isolation, idempotency,
  failure handling.
- `NotificationRecorderTest` — record/pre-check behavior.
- `ResendClientTest` — dry-run, send success/failure, timeouts.
- `NotificationTypeMappingTest` — enum ↔ DB mapping.

Not verified from the current source: Resend sender-domain verification
status; email deliverability (inbox vs spam).