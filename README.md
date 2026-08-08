# Bingee

Bingee is an early-stage, open-source Android app for tracking films and TV series. Its core promise is local-first ownership: a user's history must remain usable without an account, a proprietary backend, or continued service availability.

## Project status

The project status is Bingee `1.0.1-stable`. The app contains a local-first Compose shell, provider-independent domain foundations, secure TMDB credential management, remote movie and TV-series search, Room-backed title details, incrementally cached TV seasons and episodes, local watch progress, title-level personal ratings, Profile collection management, a Room-first Home calendar, approximate local notifications, versioned JSON backup/restore (v1), and an additive importer for documented TV Time JSON ZIP profiles. Movies and TV Series naturally include animated and anime content available from TMDB. The release details are tracked in [release notes](docs/release-notes-1.0.0-stable.md) and [roadmap](docs/roadmap.md).


Remote metadata will use a user-supplied TMDB API Read Access Token. It is optional for opening the local shell. Debug fakes are architectural fixtures and are not wired into production navigation.

## Planned stack

- Kotlin with Jetpack Compose and Material 3
- Single-activity Navigation Compose
- Hilt dependency injection
- Room for local persistence
- Retrofit and OkHttp for provider access
- Coil for constrained poster loading and caching
- WorkManager for deferrable background refresh
- Coroutines and Flow as features are added
- JVM unit tests, with focused Android instrumentation tests where they add value

The checked-in Gradle Wrapper and version catalog are the source of truth for build and dependency versions.

## Prerequisites

- Git
- JDK 21
- Android Studio or Android command-line tools
- Android SDK Platform 36.1

Android Studio normally creates a local, ignored `local.properties` file that points to the Android SDK. Never commit this file.

## Build and verify

Clone the repository, then run these commands from its root:

```bash
./gradlew tasks
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

To apply Kotlin formatting:

```bash
./gradlew spotlessApply
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Run

Open the repository in Android Studio, choose an emulator or connected device, and run the `app` configuration. From the command line, a configured device can install the debug build with:

```bash
./gradlew installDebug
```

On first run without a configured credential, Bingee offers TMDB setup or offline continuation. Bottom navigation then exposes Home, Search, and Profile. Settings is opened from Profile. Search offers explicit Movies and TV Series categories, debounces input, loads additional TMDB pages, and adds or removes results from the local collection. Search and Profile rows open title details using provider-qualified identity. Cached textual details, loaded seasons, episodes, watch progress, and personal ratings render from Room and remain usable offline or after credential removal. Episode/movie progress and 1–10 title ratings are editable without network or collection membership. Profile reads only Room and supports local title search, media/watch-state filters, and recently-added, title, progress, or rating sort. Home reads cached movie releases, season premieres, and episode air dates only from Room, includes the prior seven calendar days plus all known future events, and refreshes TMDB metadata only after an explicit user action.

WorkManager maintains a bounded batch of up to 20 followed titles approximately once per day when network is available. A separate network-free worker evaluates cached Room events for optional local notifications. Notifications are disabled by default; Settings requests Android notification permission only after the user enables them and supports same-day, one-day, three-day, or seven-day lead times plus movie, season, and episode categories. Android may delay work because of Doze, battery optimization, constraints, or device policy; Bingee promises no exact notification time.

Profile → Settings → Data & backup emits backup v1, including media history, watch progress, ratings, and preferences. Restore validates the complete file before one Room transaction. See [backup format v1](docs/backup-format-v1.md).

Profile → Settings → Data & backup also exposes an experimental `Import TV Time history` action. It supports only the role-based JSON ZIP profile derived from evidence ID `TVTIME-SAMPLE-001`. The archive is inspected locally with bounded ZIP limits, then matched conservatively through the existing TMDB credential. Ambiguous records require review or skip; confirmation applies additive, idempotent changes only. Ratings, favorites, custom lists, rewatch counters/timelines, CSV, other TV Time variants, TV Time authentication, and TV Time network access are unsupported. See [TV Time source profile](docs/imports/tv-time-source-format-v1.md) and [ADR 0019](docs/adr/0019-tv-time-import-implementation.md).

Release notes are available in [1.0.0-stable release notes](docs/release-notes-1.0.0-stable.md) and future enhancement plans in [roadmap](docs/roadmap.md).

## TMDB configuration and privacy

Bingee supports one TMDB credential format: the API Read Access Token available from the API section of a TMDB account. The app trims surrounding whitespace, checks Bearer-token structure locally, and validates the candidate with TMDB's `GET /3/authentication` endpoint. Only a remotely accepted candidate is saved.

The accepted token is encrypted with AES-256-GCM using key material held by Android Keystore. Ciphertext is stored in `noBackupFilesDir`, separate from ordinary Preferences DataStore settings and excluded from cloud backup and device transfer. The token is never included in Bingee data exports. Startup trusts a previously validated stored token and does not perform automatic remote validation.

Bingee has no account or proprietary backend. Without a usable TMDB credential, the application shell and future local data remain available while remote metadata features stay disabled. Search reads the credential only inside the protected data/network boundary; query text is not logged or stored. See [privacy notes](docs/privacy.md), [ADR 0009](docs/adr/0009-tmdb-credential-configuration.md), and [ADR 0010](docs/adr/0010-tmdb-search.md).

This product uses the TMDB API but is not endorsed or certified by TMDB. The official TMDB attribution logo is shown in Profile → Settings/About. Credentials are never part of JSON backup files.

## Architecture

Bingee starts as a single Gradle application module and a lightweight modular monolith. Packages are grouped by responsibility:

```text
app/src/main/java/com/cydoniancitizen/bingee/
  app/        application shell
  core/       domain models, results, navigation, and shared UI
  data/       Room persistence, calendar projection, secure credential storage, settings, and isolated TMDB networking
  domain/     repository contracts, refresh coordination, and local notification policy
  feature/    onboarding, settings, search, profile, details, Home calendar, and feature UI

app/src/debug/java/com/cydoniancitizen/bingee/
  debug/      deterministic repository fixtures
  feature/    deterministic Search, Profile, Details, Home, and Settings state previews
```

Feature UI depends on immutable domain models and repository contracts, never provider DTOs or Room entities. TMDB client, DTOs, mappers, errors, and identities remain isolated. Japanese animation returned by TMDB behaves identically to standard Movies and TV Series. Account sync, recommendations, and automatic merging are absent.

See [architecture conventions](docs/architecture.md) and [architecture decisions](docs/adr/) for the current boundaries and durable choices.

## Versioning

The project follows Semantic Versioning. While unreleased, development versions remain in the `0.y.z` range and may use a prerelease suffix. Milestone 0 uses `0.1.0-dev` with Android `versionCode` 1.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before changing the project. Keep pull requests small, scoped, tested, and free of credentials or machine-local files.

Security reports follow [SECURITY.md](SECURITY.md).

## License

Bingee is licensed under the [Apache License 2.0](LICENSE).
