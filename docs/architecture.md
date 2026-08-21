# Architecture conventions

Bingee v1.1.0 is a package-structured modular monolith in one Android application module. Packages provide lightweight boundaries; a future Gradle-module split requires measured value and a superseding ADR.

## Current v1.1.0 surface

- TMDB is the only runtime media provider. No Jikan or cross-provider deduplication runs in the app.
- Top-level navigation is Home, Search, and Your Bingee. The personal destination keeps the internal `profile` route and `PROFILE` identifier; only its label and icon present it as Your Bingee / Il tuo Bingee, a personal collection rather than an account.
- Your Bingee is the personal dashboard: actionable Watching, collection shortcuts with counts, Favorites, and a personal statistics preview. It opens Settings, which indexes the Appearance & Language, Notifications, Data & backup, Privacy, and About subpages, and exposes its own visible Up action alongside system Back.
- Home is Room-first and includes Continue Watching; Notification Center reads cached release events and supports local refresh feedback.
- Room v4 owns media metadata, seasons, episodes, library membership, watch progress, ratings, release events, notification state, portable preferences, the explicit serial-state override, canonical genre identity, and favorite chronology.
- Backup v1 is the versioned JSON export/restore contract and uses transactional replace restore.
- The UI ships in English and Italian. About exposes a manual GitHub update checker; it does not perform background update checks.

## Dependency direction

~~~text
app + feature + core UI
            |
            v
domain repository contracts
            |
            v
core model + core result
~~~

TMDB credential configuration follows a narrow path:

~~~text
onboarding/settings ViewModel -> TmdbCredentialRepository
                                  |-> local validator
                                  |-> encrypted no-backup store -> Android Keystore
                                  '-> validation client -> GET /3/authentication
~~~

TMDB search reuses the same protected store without exposing a token above the data layer:

~~~text
SearchScreen -> SearchViewModel -> MediaRepository
                                      |
                                      v
                              DefaultMediaRepository
                                      |
                                      v
                          TmdbSearchClient -> encrypted credential store
                                      |
                                      v
                     TmdbSearchService -> /3/search/movie or /3/search/tv
~~~

Local library state follows a separate network-free path:

~~~text
SearchScreen/ProfileScreen -> feature ViewModel -> LibraryRepository
                                                   |
                                                   v
                                      DefaultLibraryRepository
                                                   |
                                                   v
                           LibraryDao -> Room bingee.db (v4)
~~~

Your Bingee keeps current collection rendering and personal statistics on separate Room paths:

~~~text
ProfileViewModel -> LibraryRepository
                    |-> observeEntries -> current Library/Favorites UI
                    '-> observePersonalViewing -> focused watched/completed projection -> statistics
~~~

Both paths answer different questions but need the same daily progress rows, so
`DefaultLibraryRepository` shares one `observeLibraryProgress` subscription between them (and the
Details path) instead of letting Room run that projection once per collector. Statistics aggregation
runs on the injected default dispatcher, so a history-sized recomputation never lands on the main
thread.

Cache-first title details use Room as the observable source of truth:

~~~text
Search/Profile -> provider-qualified DetailRoute -> MediaDetailsViewModel
                                                    |-> LibraryRepository (membership only)
                                                    '-> MediaDetailsRepository
                                                          |-> DetailsDao -> Room Flow
                                                          '-> TmdbDetailsClient -> movie/TV details endpoint
~~~

TV metadata and personal progress follow separate paths:

~~~text
MediaDetailsViewModel -> SeriesRepository -> SeriesDao -> seasons + episodes
                                      '-> TmdbSeasonClient -> one season endpoint

MediaDetailsViewModel -> WatchProgressRepository -> WatchProgressDao
                                                   |-> episode_watch_progress
                                                   '-> movie_watch_progress

MediaDetailsViewModel -> RatingRepository -> RatingDao -> media_ratings
~~~

Home is a separate Room-first projection:

~~~text
HomeScreen -> HomeViewModel -> ReleaseCalendarRepository -> ReleaseEventDao -> Room Flow
                   |
                   '-> explicit refresh -> CalendarRefreshCoordinator
                                            |-> LibraryRepository snapshot
                                            |-> MediaDetailsRepository
                                            '-> SeriesRepository
~~~

Background maintenance keeps remote and local-only work separate:

~~~text
Application startup -> BackgroundWorkScheduler -> unique periodic WorkManager requests

CalendarRefreshWorker (CONNECTED) -> BackgroundRefreshPlanner (max 20 active titles)
                                  -> CalendarRefreshCoordinator (concurrency 3)
                                  -> immediate NotificationEvaluationWorker

NotificationEvaluationWorker (no network) -> combined preferences (Room portable + DataStore enabled)
                                          -> ReleaseEventDao due window
                                          -> notification delivery ledger
                                          -> Android notifier
~~~

Notification taps carry only provider-aware parent identity into `MainActivity`. Cold-start and `onNewIntent` targets are held until startup/onboarding resolves, navigated through the existing `DetailRoute`, then consumed once.

Local Room data renders independently of the network on every screen. Home is the one startup exception: constructing `HomeViewModel` runs a local idempotent calendar backfill and one bounded featured-discovery pass, which issues at most one `discover` movie page and one `discover` TV page, keeps at most ten interleaved results in memory, persists nothing, and returns an empty list without a stored credential. A failed featured pass leaves the screen on its cached content. The calendar itself is refreshed only by the explicit refresh action or the periodic worker; Details and Search perform their own remote work on open, query, and manual retry as described below.

- core/model and core/result are plain Kotlin. They do not import Android, Compose, Room, Retrofit, provider DTOs, DAOs, or HTTP types.
- domain/repository exposes only domain models, AppResult, suspending one-shot operations, and Flow for observable local state.
- feature owns screen UI. Composables receive immutable state and callbacks; they do not access repositories, Retrofit, OkHttp, Room, or provider DTOs.
- core/ui maps structured errors to safe resources. User-facing text and retry actions remain UI concerns.
- app owns the application shell. core/navigation owns route and top-level destination definitions.
- data packages implement repository contracts. Provider clients, separate movie/TV DTOs, mappers, and error translation remain isolated by provider.
- data/credential owns encrypted credential persistence and coordination. Raw credential text is transient input and never part of public screen state.
- data/tmdb/auth owns credential validation. data/tmdb/search owns search; data/tmdb/details owns separate movie/TV detail DTOs, mappers, and endpoints. Shared TMDB code maps HTTP failures and resolves constrained image URLs. Retrofit responses and authorization details remain inside the data layer.
- data/library owns explicit Room entities plus focused Library, Details, Series, WatchProgress, Rating, and ReleaseEvent DAOs. data/calendar owns deterministic event projection, the local repository, and the atomic metadata-plus-event write boundary. data/details owns detail-cache mapping and repository behavior; data/series owns season-cache mapping, freshness, and remote synchronization; data/progress owns local watch operations. Room types and numeric local IDs do not cross the data boundary.

Provider IDs use ExternalMediaRef(source, externalId). A raw ID is never a global identity. Room generates a numeric local media ID for foreign keys; it remains private infrastructure and does not replace external identity.

## ViewModel and screen state

- Each screen defines an immutable, screen-specific UiState; no universal generic state wrapper is used.
- A ViewModel keeps MutableStateFlow private and exposes StateFlow through asStateFlow().
- UI events enter through explicit methods or typed intents.
- viewModelScope owns long-running work. Inject a dispatcher only where deterministic tests need control.
- Persistent screen state is separate from one-off effects only when a real effect exists.
- State contains structured AppError, never raw exceptions or infrastructure messages.
- Empty shell screens do not receive ViewModels until they own state or behavior.

Production Search state distinguishes credential availability, idle/loading/empty/error, loaded pages, next-page loading/error, pagination end, observed local membership, and pending local actions. Existing results remain visible while another page loads or fails. Library state owns an in-memory query, media/state filters, sort, result count, empty/no-match distinction, pending removals, ratings, and derived progress. One `flatMapLatest` observation responds to query changes; a separate count Flow distinguishes an empty Library from filtered-out results. Details use sealed title, rating, movie-progress, series-content, and per-season load substates. Per-episode and per-season pending sets avoid unrelated blocking. Cached content remains visible during refresh and after refresh failure. Debug previews render fixed states; none is referenced by production navigation.

## Local library

- Room database `bingee.db` is version 4. Migration 1 -> 2 adds only the explicit serial-state override table; migration 2 -> 3 adds nullable provider-qualified genre identity and its composite index; migration 3 -> 4 adds the nullable `media_entries.favorite_added_at` column that gives Favorites a chronological order. Each migration is additive and non-destructive.

- `media_entries` stores list metadata, `external_refs` owns provider-qualified identity, and `library_entries` owns membership only.
- `LibraryDao` uses `Flow` for observed lists/items/membership and suspending functions for one-shot reads and writes. Multi-query add is a Room transaction. One parameterized query restricts active membership by media type and escaped localized/original-title text.
- Re-adding refreshes list metadata while preserving media creation and first-added timestamps. Removing deletes only membership and retains canonical metadata plus external references.
- Source/type enums use names, dates use ISO `LocalDate`, and timestamps use UTC `Instant`; malformed values fail safely rather than changing meaning.
- Version 4 is the canonical database version. `media_genres` identifies refreshed TMDB genres by (`source`, `genre_id`); localized `name` remains display metadata, while migrated legacy rows retain their names with null identity until a successful refresh. Serial Watch Later/Watching/Watched state derives from membership, regular-episode progress, and trustworthy metadata coverage; only explicit Abandoned intent persists. `series_watch_progress.completed_at` is genuine historical completion evidence: full covered regular progress creates it, regular progress reversal clears it, Specials cannot create it, and Library removal preserves it. No Anime-specific Room structures exist.
- Metadata contains no watched state. Progress-row absence means unwatched; a present row owns the watched timestamp.
- No TMDB token, search query, provider DTO/body, derived Library state, progress percentage, formatted calendar label, notification content, permission, or application worker state is persisted.
- Library search uses trimmed locale-independent lowercase input and escapes `\\`, `%`, and `_` for SQL `LIKE`. Media restriction and active membership happen in Room; derived-state filtering and progress/rating ordering happen in plain Kotlin to keep one source of progress rules. Stable ordering ends with title, original title, provider, and external ID.
- `media_ratings` is independent of Library membership and watch progress. Removing/re-adding a title, refreshing metadata, changing progress, or removing credentials retains the rating.
- Every later schema revision requires a version increment, non-destructive migration, new schema export, and migration tests. Destructive fallback is prohibited.

## Versioned backup and restore

- Export emits `bingee-backup` v1 and import accepts v1. V1 includes TMDB identity, favorites, watched dates, progress, membership, ratings, and preferences; restore regenerates local IDs transactionally. Freshness, provider responses, credentials, network state, WorkManager records, and delivery history remain excluded.
- Import supports only `REPLACE_PORTABLE_DATA`: parse and validate first, preview second, one explicit Room transaction last. The transaction regenerates local IDs, restores portable state, rebuilds release events, clears technical derived state, and leaves credential/permission/enablement/device runtime state outside the transaction.
- SAF uses `CreateDocument("application/json")` and `OpenDocument`; sharing uses a private cache subdirectory exposed only through a read-only `FileProvider` URI. Backup content and selected URIs are never logged.

## Experimental TV Time import

`data/imports/tvtime` is a focused source boundary, not a generic third-party importer. `TvTimeZipGateway` owns SAF reads, private temporary-copy cleanup, archive limits, and path/encryption/nested-archive checks. `TvTimeSourceParser` owns the exact `TVTIME-SAMPLE-001` role grammar and maps dedicated source DTOs into provider-neutral `Imported*` hints. It does not call TMDB or Room and retains no raw JSON. A source summary is available before matching; safe record errors and unknown-field warnings remain structural diagnostics.

`TvTimeMatcher` reuses the existing authenticated TMDB search, Find-by-external-ID, details, and season data sources. It accepts only unique compatible exact identities, a documented movie title/year proposal, and ordinary episode numbering under an accepted parent series. Series title-only results and specials remain reviewable. Requests are sequential/bounded and deduplicated for one import session; TMDB errors never mutate Room.

`TvTimeImportPlanBuilder` resolves all accepted candidates and canonical metadata before confirmation. `TvTimeImportStore` executes one additive `RoomDatabase.withTransaction` and uses `import_provenance_refs`, part of the current baseline schema rather than a later migration, for distinct TVDB, IMDb, and TV Time UUID namespaces. Existing membership, progress, ratings, credentials, portable preferences, notification delivery state, and calendar refresh state are preserved. Bingee backup restore remains the separate replace-only path.

## Local release calendar

- Event types are movie release, season premiere, and episode airing. Identity is source plus subject type, subject external ID, and event type; subject type prevents numeric collisions among media, seasons, and episodes.
- TMDB release and air dates remain ISO LocalDate. No time, UTC instant conversion, timezone shift, formatted header, relative label, watched state, rating, or membership is stored in an event row.
- Movie detail, season-summary, and season-episode writes project corresponding events in the same Room transaction. A missing returned date deletes only that subject projection; failure rolls back metadata and event changes together.
- Migration backfill plus repeatable local backfill derive events without network or credential. Backfill does not create a successful-refresh timestamp.
- Home joins events to active library membership; removal hides retained events immediately and re-add restores them with retained rating/progress. Default window starts seven calendar days before injected-clock today and has no future cutoff.
- Date groups sort ascending. Same-date rows sort episode, season, movie, normalized parent title, then provider-aware subject identity.
- Manual refresh uses at most three concurrent title operations. Movies refresh details. TV refresh first updates details/summaries, then seasons with cached episode metadata, highest regular season, and known current/future regular seasons. Season zero is selected only when episode metadata is cached or its air date lies within Home window.
- Successful and failed operations are isolated. At least one successful eligible operation permits advancing last-successful refresh after local consistency succeeds; complete failure and empty Library do not advance it.
- Background refresh and notification evaluation each run as unique approximate 24-hour work. Calendar batches 20 oldest or never-refreshed active parents and retains title concurrency three. Notification evaluation is local-only, bounded to 200 candidates in the seven-day window, and prunes ledger rows beyond 30 days.
- Current limits: no exact release time/delivery, per-event reminder, snooze, notification action/history, regional theatrical selector, provider-removal reconciliation, external calendar integration, or push messaging.

## Fake strategy

Debug fakes live in app/src/debug; debug-variant JVM tests in app/src/test reuse them. Release compilation excludes this source set. Fixtures use fixed IDs, titles, dates, and immediate results, with configurable failures and no real sleeps. No fixture reads current time, so no clock is currently needed; any future time-dependent fake must accept a Clock.

## TMDB search

- MediaSearchQuery trims only leading and trailing whitespace, requires one non-space character, preserves capitalization and internal spaces, and validates page 1–500.
- The UI selects either Movies or TV Series; it never merges endpoint rankings.
- Search waits 350 ms after query changes. New query/category/credential generations cancel active work, and generation checks suppress responses from obsolete requests.
- The ViewModel owns simple progressive paging. It appends in provider order, deduplicates by ExternalMediaRef, retains results on page failure, and stops at provider end, page 500, or a page with no new usable rows. Paging 3 is intentionally absent.
- Requests send language=en-US and include_adult=false. No genre, year, provider, region, or adult-content control exists.
- Search queries, responses, and history are not persisted or logged. No offline media-result cache exists.
- Movie and TV rows map to the same MediaSearchResult. MediaDetails remains a distinct domain model reconstructed by the detail repository.
- Records lacking a positive provider ID are skipped. A missing localized title falls back to the original title; a row with neither is skipped. Optional poster, overview, and malformed/missing dates never reject an otherwise usable row.
- Poster paths are validated and resolved inside the TMDB data package to https://image.tmdb.org/t/p/w342/.... UI receives a resolved optional URL, not a provider path. Coil loads constrained list images and owns memory/disk caching; missing/invalid/failed images use the local accessible placeholder.
- Unauthorized search responses become safe AppError.Unauthorized, preserve the encrypted credential, and offer Settings. Search does not mutate credential status behind the credential repository.

## Navigation

TopLevelDestination is the only source of Home, Search, and Your Bingee routes, order, labels, and icons; the personal destination's route stays `profile` so saved navigation state, notification targets, and the `profile/collection/{collection}` subroutes keep resolving. Settings is opened from Your Bingee and pops back through the existing back stack. BingeeNavHost owns the route-to-screen graph; reusable composables never receive a NavController. `DetailRoute` is non-top-level and carries only `MediaSource`, `MediaType`, and external provider ID. Provider/type pairs are validated before navigation; malformed routes render an input error rather than substituting another provider. `MediaType` is required to select an endpoint when a Search result has no local row. No token, local Room ID, query, URL, or metadata payload enters navigation.

AppRoute.ONBOARDING and DetailRoute are non-top-level routes. Startup reads local credential status and the non-sensitive first-run preference before constructing the graph. It starts at onboarding only for a first run without a usable stored credential; offline continuation and successful configuration both replace onboarding with Home. Removing a credential later does not force navigation away from the shell or delete cached details.

Season expansion remains state within the existing detail route. There is no season-detail or episode-detail route, and navigation carries no progress payload.

## Cache-first title details

- Movie details use `GET /3/movie/{movie_id}`; TV details use `GET /3/tv/{series_id}`. Both use `language=en-US`, the protected Bearer boundary, and base responses without appended resources.
- Cache maximum age is 24 hours. Age strictly below 24 hours is fresh; exactly 24 hours is stale. Future timestamps are stale to avoid indefinite freshness after clock changes.
- Cache miss loads remotely. Fresh cache avoids automatic network work. Stale cache renders immediately and refreshes in the background. Manual refresh always requests remote data.
- Only successful remote mapping plus atomic Room persistence advances `details_fetched_at`. Any network, mapping, or persistence failure leaves old rows and timestamp intact.
- Per-reference in-flight refreshes are coalesced; unrelated titles may refresh concurrently.
- Opening a title caches details without adding membership. Successful TMDB refresh atomically replaces ordered genre rows with stable TMDB IDs and current localized names. Removing membership retains canonical metadata, external references, details, and genres. Cache pruning is intentionally absent.
- Unsupported providers return `AppError.UnsupportedData` before any TMDB request.
- Images use TMDB CDN sizes `w342` for lists, `w500` for detail posters, and `w780` for backdrops. Text remains in Room; image bytes remain Coil-owned.

## Seasons, episodes, and watch progress

- TV-detail refresh persists provider season summaries without creating episodes. Season zero is retained and shown separately.
- Expanding a season reads cached episodes immediately. Missing or stale episode cache invokes `GET /3/tv/{series_id}/season/{season_number}`; manual retry bypasses freshness. The cache policy is an explicit 24-hour boundary.
- One season refresh is a Room transaction. It resolves the provider-qualified series and season, upserts returned metadata, retains unmatched episodes, preserves progress, and advances `episodes_fetched_at` only after success.
- Season and episode provider IDs cannot collide across providers. Numbering is parent-scoped metadata, not global identity.
- Unknown-air-date episodes are trackable. Future episodes are stored and visible but unavailable for watched actions. UTC date comes from the injected Clock.
- Single-episode, season-bulk, and movie progress writes are local Room transactions. Existing timestamps survive bulk watched actions; newly watched episodes share one action timestamp.
- Season progress is derived from trackable episodes. Overall series progress excludes season zero and requires regular trackable content. Current Library state remains derived; the separate historical completion marker records the first genuine completion timestamp.
- Removing Library membership or the TMDB credential removes neither metadata nor progress. Library progress is a local Room observation and performs no remote request.
- Current limitations include no provider-removal reconciliation and no cache pruning. There is no episode-detail screen.

## Personal viewing statistics

- One focused Room projection reads only titles with movie progress, genuine series completion evidence, or watched regular episodes. It includes membership/favorite/rating metadata without observing the broad Library query twice.
- Completed titles are watched Movies plus Series whose current canonical regular-episode progress is complete. An open Series counts while caught up; a newly available unwatched regular episode removes it until watched. Specials/Season 0 never participate.
- Episode activity sums watched regular episodes regardless of current membership or Abandoned state. Viewing time uses only persisted movie and episode runtimes, never estimates; if a required runtime is missing, the affected total is marked unavailable rather than fabricated.
- Monthly viewing uses genuine `movie_watch_progress.watched_at` and per-episode `episode_watch_progress.watched_at` in the device-local time zone. Undated legacy activity remains valid for all-time totals but is never assigned to a month or year; missing runtime marks the affected monthly value incomplete.
- Ratings and media-type distribution use the viewed/taste title cohort. Genre taste uses canonical `(source, genre_id)` identity and counts each eligible title once per genre. The Your Bingee preview keeps a deterministic top three per media type, presented as a podium whose rank is exposed as spoken text so it never depends on height or the gold/silver/bronze surfaces. Statistics 2.0 derives the complete ranking and a relative Top 6 radar from the same projection, with a transient All/Movies/Series scope.
- `watched_date` remains an optional user-selected calendar date. Completion timestamps remain precise and separate; when no user date exists, history derives the local calendar date from the genuine timestamp. `added_at` never supplies history ordering or grouping.
- Collection filtering still uses `LibraryEntry` and membership-dependent `SeriesTrackingState`; removed history does not reappear in current collection UI.
- Statistics 2.0 renders that projection as a taste radar, a full genre ranking under an All/Movies/Series scope, exact viewing analytics, a monthly histogram for a selected year, and a personal ratings histogram whose selected bucket expands a shelf of the titles behind it. All of it is derived; no chart value is persisted.

## TMDB credential security

- Only API Read Access Tokens are supported; v3 query keys and TMDB user sessions are not.
- Local syntax validation and remote authorization are separate.
- Accepted tokens are encrypted with an Android Keystore AES-256-GCM key and stored in noBackupFilesDir.
- Credential status is observable, but no status or ViewModel state carries raw token text or a token-derived mask.
- Startup trusts a successfully validated stored token until explicit replacement, removal, or retry. It performs no automatic network revalidation.
- Temporary remote failures preserve existing encrypted data. Rejected replacement candidates are never saved.
- The general OkHttp client has no logging interceptor.
- TMDB search/details attach authorization only inside their data clients. Coil reuses the application OkHttpClient for public image URLs and receives no credential header.
- The credential is outside ordinary DataStore settings and outside all current or future Bingee export models.

## Decision status

Accepted decisions are recorded in ADRs 0001–0025. ADR numbering is historical: the Room versions named in ADRs 0011–0016 belong to an earlier numbering and are not the current `bingee.db` version, which is 4. TMDB is the single media provider for Bingee. Movies and TV Series may include anime or animated content from TMDB. No Jikan runtime integration exists, and no Anime-specific Room or Backup structures exist. Backup v1 serves as the data contract. Accounts, recommendations, cloud sync, and other import formats remain deferred.
