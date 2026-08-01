# Architecture conventions

Bingee is a package-structured modular monolith in one Android application module. Packages provide lightweight boundaries; a future Gradle-module split requires measured value and a superseding ADR.

## Dependency direction

```text
app + feature + core UI
            |
            v
domain repository contracts
            |
            v
core model + core result
```

TMDB credential configuration follows a narrow path:

```text
onboarding/settings ViewModel -> TmdbCredentialRepository
                                  |-> local validator
                                  |-> encrypted no-backup store -> Android Keystore
                                  `-> validation client -> GET /3/authentication
```

- `core/model` and `core/result` are plain Kotlin. They do not import Android, Compose, Room, Retrofit, provider DTOs, DAOs, or HTTP types.
- `domain/repository` exposes only domain models, `AppResult`, suspending one-shot operations, and `Flow` for observable local state.
- `feature` owns screen UI. Composables receive immutable state and callbacks; they do not access repositories, Retrofit, OkHttp, Room, or provider DTOs.
- `core/ui` maps structured errors to safe resources. User-facing text and retry actions remain UI concerns.
- `app` owns the application shell. `core/navigation` owns route and top-level destination definitions.
- Future `data` packages may implement repository contracts. Provider clients, DTOs, mappers, and error translation remain isolated by provider.
- `data/credential` owns encrypted credential persistence and coordination. Raw credential text is transient input and never part of public screen state.
- `data/tmdb/auth` owns the only implemented TMDB endpoint. Retrofit responses and authorization details remain inside the data layer.

Provider IDs use `ExternalMediaRef(source, externalId)`. A raw ID is never a global identity. No local database ID exists until persistence needs one.

## ViewModel and screen state

- Each screen defines an immutable, screen-specific `UiState`; no universal generic state wrapper is used.
- A ViewModel keeps `MutableStateFlow` private and exposes `StateFlow` through `asStateFlow()`.
- UI events enter through explicit methods or typed intents.
- `viewModelScope` owns long-running work. Inject a dispatcher only where deterministic tests need control.
- Persistent screen state is separate from one-off effects only when a real effect exists.
- State contains structured `AppError`, never raw exceptions or infrastructure messages.
- Empty shell screens do not receive ViewModels until they own state or behavior.

`src/debug` contains `ArchitectureSampleViewModel`, its screen-specific state, deterministic fakes, and previews demonstrating repository to ViewModel to UI-state flow. None is referenced by production navigation.

## Fake strategy

Debug fakes live in `app/src/debug`; debug-variant JVM tests in `app/src/test` reuse them. Release compilation excludes this source set. Fixtures use fixed IDs, titles, dates, and immediate results, with configurable failures and no real sleeps. No fixture reads current time, so no clock is currently needed; any future time-dependent fake must accept a `Clock`.

## Navigation

`TopLevelDestination` is the only source of top-level routes, order, labels, and icons. `BingeeNavHost` owns the route-to-screen graph; reusable composables never receive a `NavController`. Argument-bearing destinations must add their route definition in `core/navigation` rather than spreading raw strings. Typed routes remain deferred until arguments make them simpler than centralized strings.

`AppRoute.ONBOARDING` is the sole non-top-level route. Startup reads local credential status and the non-sensitive first-run preference before constructing the graph. It starts at onboarding only for a first run without a usable stored credential; offline continuation and successful configuration both replace onboarding with Home. Removing a credential later does not force navigation away from the shell.

## TMDB credential security

- Only API Read Access Tokens are supported; v3 query keys and TMDB user sessions are not.
- Local syntax validation and remote authorization are separate.
- Accepted tokens are encrypted with an Android Keystore AES-256-GCM key and stored in `noBackupFilesDir`.
- Credential status is observable, but no status or ViewModel state carries raw token text or a token-derived mask.
- Startup trusts a successfully validated stored token until explicit replacement, removal, or retry. It performs no automatic network revalidation.
- Temporary remote failures preserve existing encrypted data. Rejected replacement candidates are never saved.
- The general OkHttp client has no logging interceptor. Future authenticated services must attach authorization inside the data/network boundary.
- The credential is outside ordinary DataStore settings and outside all current or future Bingee export models.

## Decision status

Accepted decisions are recorded in ADRs 0001–0009. Model fields, media repository signatures, and the debug presentation fixture remain intentionally provisional until media and database milestones exercise them. Local IDs, watch progress, typed argument routes, and extra media repositories are deferred.
