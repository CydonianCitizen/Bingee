# Privacy notes (v1.1.0)

Bingee is local-first and currently has no Bingee account, proprietary backend, analytics, or crash-reporting service.

TMDB is the only runtime media provider. The About screen can query GitHub only when the user manually checks for an update; this request does not include the TMDB credential or personal tracking data.

## TMDB credential

Bingee supports a user-supplied TMDB API Read Access Token. After TMDB validates it, Bingee encrypts it locally with Android Keystore-backed key material and stores the ciphertext in Android's no-backup directory. The raw token is sent only to TMDB in the HTTPS Authorization header when an authenticated TMDB request is required.

The token is not stored in ordinary preferences, Room, navigation arguments, saved instance state, logs, screenshots, previews, or test fixtures. It is excluded from Android cloud backup, device transfer, and Bingee JSON data exports. Removing it deletes the encrypted file and the dedicated Keystore key. Temporary network or TMDB service failures do not erase an existing token.

## TV Time import privacy

The experimental TV Time importer accepts only the documented JSON ZIP profile derived from `TVTIME-SAMPLE-001`. The selected SAF URI is used transiently, is never persisted or logged, and the archive is copied only to the app-private cache for bounded inspection before cleanup. No raw ZIP, extracted file, raw JSON, source path, provider response body, title history, or source record is persisted or logged. Bingee never authenticates with TV Time and never uploads the archive to TV Time or Bingee.

TMDB is contacted only after the user starts matching and only through the existing protected TMDB clients. Matching state contains safe source titles, qualified identifiers, candidates, warnings, and review actions; it contains no token, URI, Room ID, or raw JSON. The final confirmation is additive: it may create canonical metadata, Library membership, source-provenance references, required season/episode metadata, missing progress, and release-event projections. It never removes local data, overwrites existing progress timestamps or ratings, resets credentials, preferences, notification state, delivery history, or calendar refresh state, and it posts no notification. Ratings, favorites, custom lists, and rewatch counters/timelines are intentionally not imported.

## Current network behavior

Bingee calls TMDB's `GET /3/authentication` endpoint when the user explicitly validates or retries a credential. Search uses separate `GET /3/search/movie` and `GET /3/search/tv` requests after a 350 ms debounce. Details use `GET /3/movie/{movie_id}` or `GET /3/tv/{series_id}` only when cache policy or manual refresh requires it. Expanding or manually refreshing a TV season may call `GET /3/tv/{series_id}/season/{season_number}`. Home never performs automatic network work; its explicit refresh action calls the same protected detail and season repositories for active Library titles with bounded concurrency. Bingee does not validate on every startup and performs no background credential checks.

Search query text is held only in current screen memory. Bingee does not log it, persist search history, or cache result pages in long-lived storage. Normalized public title, season, and episode metadata plus fetch timestamps are cached in Room for offline use; raw provider responses are not persisted.

Watched timestamps and integer title ratings are personal history stored locally in dedicated progress and rating tables. Derived date-only release events and last successful manual calendar-refresh time are stored in dedicated Room tables; they contain no watched state, rating, membership, credential, query, provider response body, or formatted label. Events for removed Library titles remain cached but are excluded from Home by a local membership join. Calendar history, ratings, and progress are not logged. These tables follow the same intended platform backup policy as the Room database. The protected TMDB credential remains separately excluded from backup and device transfer. Users can create a separate versioned plaintext JSON backup for portable personal data; its scope and exclusions are documented below.

Optional release notifications are disabled by default. Device-local enablement remains in Preferences DataStore. Portable lead time and movie/season/episode category choices are stored in Room and can be included in the versioned JSON backup. Room stores a minimal delivery identity plus deterministic notification ID and delivery timestamp so the same provider-aware event/date/lead configuration is not notified twice. It stores no rendered notification text, title, overview, watched state, rating, credential, or provider response. Delivery rows are technical state: they are excluded from JSON backup and cleared on portable restore.

The network-constrained calendar worker refreshes at most 20 active titles per run through existing protected repositories; no token enters WorkManager input, tags, state, logs, or notifications. The separate notification worker requires no network or credential and reads only cached active-Library events. Notification taps carry only parent provider, media type, and external ID. Android background timing is approximate; Bingee uses no exact alarm, foreground service, Firebase, cloud message, analytics, or notification action that changes personal state.

Library search text, media/state filters, and sort selection exist only in current screen memory. Library queries are not logged, persisted, sent to TMDB, or added to search history. Local organization observes only active Library membership while retained non-member metadata, progress, and ratings remain private and available to title details.

Cached metadata and progress may belong to Library members or non-members and remain after Library removal or credential removal. Posters, backdrops, and episode stills use constrained TMDB image URLs; Coil owns image memory/disk caching.

## JSON backup privacy

Settings can save or share a versioned plaintext JSON backup through Android's Storage Access Framework and system Sharesheet. It may contain Library membership, watched timestamps, ratings, title metadata, and selected notification categories. It is not encrypted or password protected. The TMDB credential, encrypted credential material, Keystore aliases, device permission/enablement state, WorkManager state, notification-delivery history, internal Room IDs, raw provider responses, search text, and file URI are excluded.

Restore accepts canonical Bingee v1 files. V1 may contain TMDB identity, cached metadata, membership, progress, ratings, and preferences, but no credential, freshness, request state, or raw response. The complete document is bounded and semantically validated before one replace transaction; failures leave prior portable state unchanged. Release events are rebuilt locally without network calls or notifications.

## TMDB attribution

Bingee uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB. The TMDB logo shown in Settings/About is an approved TMDB attribution asset and remains less prominent than Bingee branding.
