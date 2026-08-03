# Architecture conventions

Bingee is a package-structured modular monolith in one Android application module. Packages provide lightweight boundaries; a future Gradle-module split requires measured value and a superseding ADR.

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
SearchScreen/LibraryScreen -> feature ViewModel -> LibraryRepository
                                                   |
                                                   v
                                      DefaultLibraryRepository
                                                   |
                                                   v
                           LibraryDao -> Room bingee.db (v2)
~~~

Cache-first title details use Room as the observable source of truth:

~~~text
Search/Library -> provider-qualified DetailRoute -> MediaDetailsViewModel
                                                    |-> LibraryRepository (membership only)
                                                    '-> MediaDetailsRepository
                                                          |-> DetailsDao -> Room Flow
                                                          '-> TmdbDetailsClient -> movie/TV details endpoint
~~~

- core/model and core/result are plain Kotlin. They do not import Android, Compose, Room, Retrofit, provider DTOs, DAOs, or HTTP types.
- domain/repository exposes only domain models, AppResult, suspending one-shot operations, and Flow for observable local state.
- feature owns screen UI. Composables receive immutable state and callbacks; they do not access repositories, Retrofit, OkHttp, Room, or provider DTOs.
- core/ui maps structured errors to safe resources. User-facing text and retry actions remain UI concerns.
- app owns the application shell. core/navigation owns route and top-level destination definitions.
- data packages implement repository contracts. Provider clients, separate movie/TV DTOs, mappers, and error translation remain isolated by provider.
- data/credential owns encrypted credential persistence and coordination. Raw credential text is transient input and never part of public screen state.
- data/tmdb/auth owns credential validation. data/tmdb/search owns search; data/tmdb/details owns separate movie/TV detail DTOs, mappers, and endpoints. Shared TMDB code maps HTTP failures and resolves constrained image URLs. Retrofit responses and authorization details remain inside the data layer.
- data/library owns explicit Room entities plus LibraryDao and DetailsDao. data/details owns detail cache mappings, freshness policy, and the production MediaDetailsRepository. Room types and numeric local IDs do not cross the data boundary.

Provider IDs use ExternalMediaRef(source, externalId). A raw ID is never a global identity. Room generates a numeric local media ID for foreign keys; it remains private infrastructure and does not replace external identity.

## ViewModel and screen state

- Each screen defines an immutable, screen-specific UiState; no universal generic state wrapper is used.
- A ViewModel keeps MutableStateFlow private and exposes StateFlow through asStateFlow().
- UI events enter through explicit methods or typed intents.
- viewModelScope owns long-running work. Inject a dispatcher only where deterministic tests need control.
- Persistent screen state is separate from one-off effects only when a real effect exists.
- State contains structured AppError, never raw exceptions or infrastructure messages.
- Empty shell screens do not receive ViewModels until they own state or behavior.

Production Search state distinguishes credential availability, idle/loading/empty/error, loaded pages, next-page loading/error, pagination end, observed local membership, and pending local actions. Existing results remain visible while another page loads or fails. Library state distinguishes loading/empty/error/entries, structural filter, and pending removals. Details use a sealed resolving/loading/content/full-error state plus orthogonal refresh, membership, and membership-action state. Cached content remains visible during refresh and after refresh failure. Debug previews render fixed states; none is referenced by production navigation.

## Local library

- Room database `bingee.db` is version 2; schema versions 1 and 2 are generated through KSP into `app/schemas/` and version controlled.
- `media_entries` stores list metadata, `external_refs` owns provider-qualified identity, and `library_entries` owns membership only.
- `LibraryDao` uses `Flow` for observed lists/items/membership and suspending functions for one-shot reads and writes. Multi-query add is a Room transaction.
- Re-adding refreshes list metadata while preserving media creation and first-added timestamps. Removing deletes only membership and retains canonical metadata plus external references.
- Source/type enums use names, dates use ISO `LocalDate`, and timestamps use UTC `Instant`; malformed values fail safely rather than changing meaning.
- Version 2 adds one-to-one `media_details` and ordered `media_genres`. Details store only normalized fields and a successful fetch timestamp; no provider DTO/body is stored. Migration 1-to-2 only creates these empty structures, preserving every v1 row and timestamp.
- No TMDB token, search query, watched state, rating, season, episode, or release event is persisted.
- Every later schema revision requires a version increment, non-destructive migration, new schema export, and migration tests. Destructive fallback is prohibited.

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

TopLevelDestination is the only source of Home, Search, Library, and Settings routes, order, labels, and icons. BingeeNavHost owns the route-to-screen graph; reusable composables never receive a NavController. `DetailRoute` is non-top-level and carries only `MediaSource`, `MediaType`, and external provider ID. `MediaType` is required to select an endpoint when a Search result has no local row. No token, local Room ID, query, URL, or metadata payload enters navigation.

AppRoute.ONBOARDING and DetailRoute are non-top-level routes. Startup reads local credential status and the non-sensitive first-run preference before constructing the graph. It starts at onboarding only for a first run without a usable stored credential; offline continuation and successful configuration both replace onboarding with Home. Removing a credential later does not force navigation away from the shell or delete cached details.

## Cache-first title details

- Movie details use `GET /3/movie/{movie_id}`; TV details use `GET /3/tv/{series_id}`. Both use `language=en-US`, the protected Bearer boundary, and base responses without appended resources.
- Cache maximum age is 24 hours. Age strictly below 24 hours is fresh; exactly 24 hours is stale. Future timestamps are stale to avoid indefinite freshness after clock changes.
- Cache miss loads remotely. Fresh cache avoids automatic network work. Stale cache renders immediately and refreshes in the background. Manual refresh always requests remote data.
- Only successful remote mapping plus atomic Room persistence advances `details_fetched_at`. Any network, mapping, or persistence failure leaves old rows and timestamp intact.
- Per-reference in-flight refreshes are coalesced; unrelated titles may refresh concurrently.
- Opening a title caches details without adding membership. Removing membership retains canonical metadata, external references, details, and genres. Cache pruning is intentionally absent.
- Unsupported providers return `AppError.UnsupportedData` before any TMDB request.
- Images use TMDB CDN sizes `w342` for lists, `w500` for detail posters, and `w780` for backdrops. Text remains in Room; image bytes remain Coil-owned.

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

Accepted decisions are recorded in ADRs 0001–0012. Watch progress, typed argument routes, background refresh, cache pruning, and extra providers remain deferred.
