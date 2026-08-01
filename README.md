# Bingee

Bingee is an early-stage, open-source Android app for tracking films and TV series. Its core promise is local-first ownership: a user's history must remain usable without an account, a proprietary backend, or continued service availability.

## Project status

The project is unreleased and currently at **Milestone 2 — Settings and TMDB Credential Configuration**. The app contains a local-first Compose shell, provider-independent domain foundations, first-run TMDB configuration, secure credential management in Settings, and explicit offline continuation. Media search, library storage, background work, notifications, and import/export are planned but not implemented.

Remote metadata will use a user-supplied TMDB API Read Access Token. It is optional for opening the local shell. Debug fakes are architectural fixtures and are not wired into production navigation.

## Planned stack

- Kotlin with Jetpack Compose and Material 3
- Single-activity Navigation Compose
- Hilt dependency injection
- Room for local persistence
- Retrofit and OkHttp for provider access
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

On first run without a configured credential, Bingee offers TMDB setup or offline continuation. Bottom navigation then exposes Home, Search, and Settings. Search remains an honest placeholder until Milestone 3.

## TMDB configuration and privacy

Bingee supports one TMDB credential format: the API Read Access Token available from the API section of a TMDB account. The app trims surrounding whitespace, checks Bearer-token structure locally, and validates the candidate with TMDB's `GET /3/authentication` endpoint. Only a remotely accepted candidate is saved.

The accepted token is encrypted with AES-256-GCM using key material held by Android Keystore. Ciphertext is stored in `noBackupFilesDir`, separate from ordinary Preferences DataStore settings and excluded from cloud backup and device transfer. The token is never included in Bingee data exports. Startup trusts a previously validated stored token and does not perform automatic remote validation.

Bingee has no account or proprietary backend. Without a usable TMDB credential, the application shell and future local data remain available while remote metadata features stay disabled. See [privacy notes](docs/privacy.md) and [ADR 0009](docs/adr/0009-tmdb-credential-configuration.md).

This product uses the TMDB API but is not endorsed or certified by TMDB. The official TMDB attribution logo is shown in Settings/About.

## Architecture

Bingee starts as a single Gradle application module and a lightweight modular monolith. Packages are grouped by responsibility:

```text
app/src/main/java/com/cydoniancitizen/bingee/
  app/        application shell
  core/       domain models, results, navigation, and shared UI
  data/       secure credential storage, ordinary settings, and narrow TMDB auth networking
  domain/     repository contracts
  feature/    onboarding, settings, search shell, and feature UI

app/src/debug/java/com/cydoniancitizen/bingee/
  debug/      deterministic fakes, sample ViewModel, and previews
```

Feature UI may depend on domain models and repository contracts, but composables receive state and callbacks rather than repositories. Domain code does not depend on Android UI, Compose, Retrofit, Room, provider DTOs, or DAOs. Infrastructure implementations will remain behind repository contracts when later milestones add them.

See [architecture conventions](docs/architecture.md) and [architecture decisions](docs/adr/) for the current boundaries and durable choices.

## Versioning

The project follows Semantic Versioning. While unreleased, development versions remain in the `0.y.z` range and may use a prerelease suffix. Milestone 0 uses `0.1.0-dev` with Android `versionCode` 1.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and `AGENTS.md` before changing the project. Keep pull requests small, scoped, tested, and free of credentials or machine-local files.

Security reports follow [SECURITY.md](SECURITY.md).

## License

Bingee is licensed under the [Apache License 2.0](LICENSE).
