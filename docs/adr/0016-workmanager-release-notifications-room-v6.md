# ADR 0016: WorkManager refresh and local release notifications with Room v6

## Status

Accepted — 2026-08-04

## Context

Bingee must maintain followed-title metadata while closed and optionally notify from cached release events. Android background execution is approximate, notification permission is user-controlled, and duplicate delivery must survive process death without coupling personal notification state to provider metadata.

## Decision

- Configure WorkManager through `HiltWorkerFactory`; workers are injected `CoroutineWorker`s and receive no credential text or WorkRequest input.
- Register `calendar_refresh_periodic` and `release_notification_periodic` as unique 24-hour periodic work with `ExistingPeriodicWorkPolicy.UPDATE`. Register `release_notification_immediate` as unique one-time work with `ExistingWorkPolicy.KEEP`.
- Calendar work requires a connected network. Notification evaluation has no network constraint. Neither work is expedited or foreground.
- Calendar work selects at most 20 active Library parents, ordering missing detail-refresh timestamps first, then oldest timestamps, provider, external ID, and media type. Existing title concurrency remains three; existing TV-season selection remains unchanged. A successful refresh moves a title behind older candidates naturally.
- Complete transient network/server/rate-limit or safe Room failures retry with exponential 30-minute backoff. One chain makes at most three attempts; missing/rejected/unreadable credentials, empty work, partial success, permission/capability blocks, and non-transient failures wait for the next periodic run. Invalid worker configuration fails.
- Notifications are opt-in. Defaults are disabled, one day before, with movie, season, and episode categories selected. Ordinary preferences use the existing Preferences DataStore, separate from protected credential storage.
- Create one stable `release_updates` channel with default importance and private lock-screen visibility. Request `POST_NOTIFICATIONS` only after the Settings enable action. Denial leaves the preference disabled; blocked states offer application notification settings.
- Query active-Library events from today through seven days ahead. For selected lead days `0`, `1`, `3`, or `7`, an event is due when `eventDate - leadDays <= today <= eventDate`. WorkManager delivery time remains approximate and no event time is fabricated.
- Room v6 adds `notification_deliveries`. Its composite identity is source, subject type, subject external ID, event type, event date, and lead days. Rows contain only that identity, deterministic notification ID, and delivery timestamp.
- Post first, then persist delivery. A persistence failure can repost the same deterministic ID; `setOnlyAlertOnce(true)` limits repeated alerts. The ledger, not the integer ID, is deduplication truth because digest collisions are possible. Prune event dates strictly older than the 30-day retention boundary.
- Notification PendingIntents are explicit and immutable. They carry only parent source, media type, and external ID. Movie notifications open movie details; season and episode notifications open the existing TV-series detail route. `singleTop`, `onNewIntent`, and one-time target consumption support cold and warm starts without adding season/episode routes.
- Do not use exact alarms, foreground services, push messaging, custom receivers, notification actions, or per-title/per-event reminder state.

## Consequences

Android may delay or skip a periodic run because of Doze, battery optimization, constraints, or device policy; UI and documentation must not promise an exact hour or minute. Cached events remain usable and locally notifiable without network or credentials. Delivery rows follow Room backup policy and may prevent duplicate notifications after restore. Notification preferences follow ordinary DataStore backup policy; the TMDB credential remains excluded.
